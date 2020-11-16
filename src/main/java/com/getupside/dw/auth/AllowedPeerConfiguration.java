package com.getupside.dw.auth;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.util.StringUtils;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getupside.dw.auth.dao.AWSSecretsManagerPeerDAO;
import com.google.common.cache.CacheBuilderSpec;

import static com.google.common.base.Preconditions.checkNotNull;

import com.getupside.dw.auth.model.Peer;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.Authorizer;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.PermitAllAuthorizer;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.util.Collection;


/**
 * <p>Container for configuration, in the "config + factory" pattern that DropWizard likes</p>
 * <p>This configuration assumes the only store of BasicAuth usernames and passwords will come from AWS Secrets Manager</p>
 * <p>If a cachePolicy is set, then the Authenticator that is registered with Jersey upon calling {@code registerAuthenticator}
 * will be a CachingAuthenticator.  Otherwise, it'll be an instance of {@code AllowedPeerAuthenticator}</p>
 */
public class AllowedPeerConfiguration {

    @JsonProperty("realm")
    private String realm = "peers";

    @JsonProperty("cachePolicy")
    private CacheBuilderSpec cachePolicy;

    /**
     * A comma separated string of AWS Secrets Manager coorindates,
     * e.g. "service/prod/echo/auth/general, service/prod/echo/auth/admin"
     *
     * Also supports a string that starts with "mock:/" to load JSON username passwords from the classpath, e.g.
     * A file in src/main/resources/x/y/z/allowed-peers.json containing json:
     * {
     *     "user1": "secret"
     * }
     *
     * Will be used instead of AWS Secrets Manager if this secretsCoordinate string is set to:
     * "mock:/x/y/z/allowed-peers.json"
     */
    @JsonProperty("secretCoordinates")
    private String secretCoordinates;

    // Allow setting this client to support testing
    private AWSSecretsManager awsSecretsManager;

    /**
     * @return  BasicAuth Realm (name not really important; just needed for response
     * http://tools.ietf.org/html/rfc2617#section-3.2.1)
     */
    public String getRealm() {
        return realm;
    }

    /**
     * @param realm  BasicAuth Realm (name not really important; just needed for response
     * http://tools.ietf.org/html/rfc2617#section-3.2.1).  If not set, it defaults to "peers"
     */
    public void setRealm(String realm) {
        this.realm = realm;
    }

    /**
     * @return A String conforming to Guava's CacheBuilderSpec that is used if/when returning a CachingAuthenticator.
     */
    public CacheBuilderSpec getCachePolicy() {
        return cachePolicy;
    }

    /**
     * @param cachePolicy A String conforming to Guava's CacheBuilderSpec that is used if/when returning a CachingAuthenticator.
     */
    public void setCachePolicy(CacheBuilderSpec cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    /**
     * @return The name(s) of the secret coordinate(s) to lookup in AWS's SecretManager.  Note that an AWS secret is
     * itself a Json block of key,value pairs, so multiple secret keys may be referenced by requesting
     * a single 'secret coordinate'
     */
    public Collection<String> getSecretCoordinates() {
        String[] coordinates = secretCoordinates.split(",");
        ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
        for (String coordinate : coordinates) {
            builder.add(StringUtils.trim(coordinate));
        }
        return builder.build();
    }

    /**
     * Implementation note - this setter is a string, but the getter is a Collection of Strings because it's difficult
     * to describe a YAML list in an environment variable override, such as what a Docker container might wish to set
     *
     * @param secretCoordinates The comma separated name(s) of one or more secret coordinates
     * in AWS's SecretManager that holds the BasicAuth credentials we want to use for our surrounding dropwizard service
     */
    public void setSecretCoordinates(String secretCoordinates) {
        this.secretCoordinates = secretCoordinates;
    }


    /**
     * <p>An authenticator that uses AWS Secrets Manager to fetch the BasicAuth usernames and passwords the enclosing
     * service will permit access.</p>
     * @return An Authenticator appropriate for registering with Jersey as described
     * https://dropwizard.github.io/dropwizard/manual/auth.html
     */
    public Authenticator<BasicCredentials, Peer> createAuthenticator() {
        return new AllowedPeerAuthenticator(new AWSSecretsManagerPeerDAO(getAWSSecretsManager(),
                                                                         getSecretCoordinates()));
    }

    private AWSSecretsManager getAWSSecretsManager() {
        if (this.awsSecretsManager == null) {
            this.awsSecretsManager = createDefaultAWSSecretsManager();
        }
        return this.awsSecretsManager;
    }

    void setAWSSecretsManager(AWSSecretsManager awsSecretsManager) {
        this.awsSecretsManager = awsSecretsManager;
    }

    private AWSSecretsManager createDefaultAWSSecretsManager() {
        return AWSSecretsManagerClientBuilder.standard()
            .withCredentials(getAWSCredentialsProvider())
            .withRegion(getAwsRegionProvider().getRegion())
            .build();
    }

    private AWSCredentialsProvider getAWSCredentialsProvider() {
        return new DefaultAWSCredentialsProviderChain();
    }

    private AwsRegionProvider getAwsRegionProvider() {
        return new DefaultAwsRegionProviderChain();
    }

    /**
     * @param metrics A metrics registry
     * @return The Authenticator you'd get by calling {@code createAuthenticator} directly, but wrapped in the Dropwizard
     * CachingAuthenticator proxy with this configuration object's {@code cachePolicy} applied to it.
     */
    public CachingAuthenticator<BasicCredentials, Peer> createCachingAuthenticator(MetricRegistry metrics) {
        checkNotNull(this.cachePolicy, "Illegal call to createCachingAuthenticator() when the configuration "
                + "object's cachePolicy attribute is null");
        return new CachingAuthenticator<>(metrics, createAuthenticator(), this.cachePolicy);
    }

    /**
     * This method registers the authenticator configured in this Configuration class with Jersey with a PermitAllAuthorizer
     * @param environment A DropWizard environment
     */
    public void registerAuthenticator(Environment environment) {
        registerAuthenticator(environment, new PermitAllAuthorizer<>());
    }

    /**
     *
     * @param environment The Dropwizard environment
     * @param authorizer A specific authorizer to use instead of the default PermitAllAuthorizer.  See
     * https://www.dropwizard.io/1.3.12/docs/manual/auth.html for more details
     */
    public void registerAuthenticator(Environment environment, Authorizer<Peer> authorizer) {
        checkNotNull(environment, "Illegal call to registerAuthenticator with a null Environment object");
        Authenticator<BasicCredentials, Peer> authenticator;
        if (this.cachePolicy != null) {
            authenticator = createCachingAuthenticator(environment.metrics());
        }
        else {
            authenticator = createAuthenticator();
        }
        environment.jersey().register(new AuthDynamicFeature(
            new BasicCredentialAuthFilter.Builder<Peer>()
                .setAuthenticator(authenticator)
                .setAuthorizer(authorizer)
                .setRealm(this.realm)
                .buildAuthFilter()));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(Peer.class));
    }
}
