package com.getupside.dw.auth;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilderSpec;
import com.getupside.dw.auth.model.Peer;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * <p>Tests the AllowedPeerConfiguration</p>
 */
public class TestAllowedPeerConfiguration {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Test
    public void testCreateAuthentorWithBadConfiguration() {
        AllowedPeerConfiguration config = new AllowedPeerConfiguration();

        expectedException.expect(NullPointerException.class);
        config.createAuthenticator();
    }

    @Test
    public void testCreateCachingAuthentiator() throws AuthenticationException {
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId("x/y/z");
        GetSecretValueResult result = new GetSecretValueResult().withSecretString("{\"foo\": \"bar\"}");

        AWSSecretsManager awsSecretsManager = createNiceMock(AWSSecretsManager.class);
        expect(awsSecretsManager.getSecretValue(request)).andReturn(result);
        replay(awsSecretsManager);

        AllowedPeerConfiguration config = new AllowedPeerConfiguration();
        config.setSecretCoordinates("x/y/z");
        config.setAWSSecretsManager(awsSecretsManager);

        config.setCachePolicy(CacheBuilderSpec.parse("maximumSize=100, expireAfterAccess=10m"));

        CachingAuthenticator<BasicCredentials, Peer> cachingAuthenticator =
                config.createCachingAuthenticator(new MetricRegistry());
        assertTrue(cachingAuthenticator.authenticate(new BasicCredentials("foo", "bar")).isPresent());
    }

    /**
     * Make sure we can split up a String CSV and trim all the pieces
     */
    @Test
    public void testMultipleSecretCoordinates() {
        AllowedPeerConfiguration config = new AllowedPeerConfiguration();
        config.setSecretCoordinates("  a/b/c, d/e/f   ");

        assertTrue(config.getSecretCoordinates().containsAll(ImmutableList.of("a/b/c", "d/e/f")));
    }


    @Test
    public void testCreateCachingAuthenticatorWithNoCachePolicy() {
        AllowedPeerConfiguration config = new AllowedPeerConfiguration();

        expectedException.expect(NullPointerException.class);
        config.createCachingAuthenticator(new MetricRegistry());
    }
}
