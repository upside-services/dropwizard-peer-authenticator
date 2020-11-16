package com.getupside.dw.auth.dao;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.google.common.collect.ImmutableList;
import com.getupside.dw.auth.model.Peer;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This integration test only works within the GetUpside environment;  if you're forking/using this in your
 * environment either comment this out or replace this test with an equivalent test in your environment
 * to demonstrate your changes to this plugin work before integrating the JAR with your DW service.
 */
public class IntTestAWSSecretsManagerPeerDAO {

    private static final Peer admin = new Peer("admin", "xxx");
    private static final Peer user1 = new Peer("user_1", "xxx");
    private static final Peer user2 = new Peer("user_2", "xxx");

    @Ignore
    @Test
    public void testServiceAlphaEcho() {
        Collection<String> secretCoordinates = ImmutableList.of("service/alpha/echo/basic_auth/admin");

        AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard().withRegion(Regions.US_EAST_1);
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(clientBuilder.build(), secretCoordinates);

        Set<Peer> peers = dao.findAll();

        assertEquals(1, peers.size());
        assertTrue("Expected 'admin' Peer to exist at the basic_auth/admin coordinate", peers.contains(admin));
    }

    @Ignore
    @Test
    public void testServiceAlphaEchoMultipleSecrets() {
        Collection<String> secretCoordinates = ImmutableList.of("service/alpha/echo/basic_auth/admin",
                                                                "service/alpha/echo/basic_auth/general");

        AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard().withRegion(Regions.US_EAST_1);
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(clientBuilder.build(), secretCoordinates);

        Set<Peer> peers = dao.findAll();

        assertEquals(3, peers.size());
        assertTrue(peers.containsAll(ImmutableList.of(admin, user1, user2)));
    }
}
