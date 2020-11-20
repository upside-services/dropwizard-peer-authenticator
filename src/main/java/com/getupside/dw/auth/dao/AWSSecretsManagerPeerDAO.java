package com.getupside.dw.auth.dao;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.getupside.dw.auth.model.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AWSSecretsManagerPeerDAO implements PeerDAO {

    // If our secretId begins with this String, this SecretProvider will load a JSON map from the
    // classpath location following this string and use that as the source of BasicAuth
    public static final String MOCK_SECRET_PREFIX = "mock:";
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AWSSecretsManager client;
    private final Collection<String> secretCoordinates;
    private final ObjectMapper objectMapper;

    public AWSSecretsManagerPeerDAO(AWSSecretsManager client,
                                    Collection<String> secretCoordinates) {

        checkNotNull(secretCoordinates, "Must provide non-null secretCoordinates");

        // Require a non-null client to AWS SecretsManager unless all the secret coordinates are
        // mock (local classpath) values
        for (String secretCoordinate : secretCoordinates) {
            if (!secretCoordinate.startsWith(MOCK_SECRET_PREFIX)) {
                checkNotNull(client, "Must provide non-null AWSSecretsManager client");
                break;
            }
        }

        LOGGER.debug("Constructing new AWSSecretsManagerPeerDAO from secretCoordinates '{}'", secretCoordinates);

        this.secretCoordinates = secretCoordinates;

        this.client  = client;

        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public Set<Peer> findAll() {

        try {
            ImmutableSet.Builder<Peer> builder = ImmutableSet.builder();

            for (String secretCoordinate : this.secretCoordinates) {
                String secretJson = lookupSecret(secretCoordinate);

                Map<String, String> secretMap = jsonToMap(secretJson);

                secretMap.forEach((k, v) -> {
                    builder.add(new Peer(k, v));
                });
            }

            return builder.build();

        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String lookupSecret(String secretName) {
        checkNotNull(secretName);
        if (secretName.startsWith(MOCK_SECRET_PREFIX)) {
            String classpathMockSecret = secretName.substring(MOCK_SECRET_PREFIX.length());
            LOGGER.debug("Loading allowed peers for BasicAuth requests " +
                             "from mock classpath '{}'", classpathMockSecret);
            return lookupMockSecret(classpathMockSecret);
        }
        else {
            LOGGER.debug("Loading allowed peers for BasicAuth request " +
                             "from AWS SecretsManager coordinate '{}'", secretName);
            return lookupAWSSecret(secretName);
        }
    }

    private String lookupMockSecret(String classpathMockResource) {
        InputStream fixtureStream = this.getClass().getResourceAsStream(classpathMockResource);

        return new BufferedReader(new InputStreamReader(fixtureStream))
            .lines()
            .collect(Collectors.joining("\n"));
    }

    private String lookupAWSSecret(String secretName) {
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretName);
        GetSecretValueResult getSecretValueResult = this.client.getSecretValue(request);

        // Decrypts secret using the associated KMS CMK.
        // Depending on whether the secret is a string or binary, one of these fields will be populated.
        String secretJson;
        if (getSecretValueResult.getSecretString() != null) {
            secretJson = getSecretValueResult.getSecretString();
        }
        else {
            secretJson = new String(Base64.getDecoder()
                                        .decode(getSecretValueResult
                                                    .getSecretBinary())
                                        .array());
        }

        return secretJson;
    }

    // package private for testing
    Map<String, String> jsonToMap(String secret) throws IOException {
        return this.objectMapper.readValue(secret, new TypeReference<Map<String, String>>() {});
    }
}
