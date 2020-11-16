package com.getupside.dw.auth.dao;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.getupside.dw.auth.model.Peer;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAWSSecretsManagerPeerDAO {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AWSSecretsManager secretsManagerClient = createNiceMock(AWSSecretsManager.class);

    private Collection<String> secretCoordinates = ImmutableList.of("foo/secret");

    @Before()
    public void setup() {
        replay(this.secretsManagerClient);
    }

    @After()
    public void verifyMock() {
        verify(this.secretsManagerClient);
    }

    @Test
    public void testNullRegionConstruction() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("non-null AWSSecretsManager client");
        new AWSSecretsManagerPeerDAO(null, this.secretCoordinates);
    }

    @Test
    public void testNullSecretNameConstruction() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("non-null secretCoordinates");
        new AWSSecretsManagerPeerDAO(this.secretsManagerClient,null);
    }

    @Test
    public void testJsonToMap() throws IOException {
        String json = "{\"db_username\":\"test\"," +
            "\"db_password\":\"supersecretpassword\"," +
            "\"upside\":\"xxxxxxx\"," +
            "\"upside2\":\"yyyyyyy\"}";
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(this.secretsManagerClient, this.secretCoordinates);

        Map<String, String> map = dao.jsonToMap(json);
        assertEquals(4, map.size());
        assertEquals("xxxxxxx", map.get("upside"));
        assertEquals("yyyyyyy", map.get("upside2"));
    }

    @Test
    public void testMultipleSecretCoordinates() {
        GetSecretValueRequest request1 = new GetSecretValueRequest().withSecretId("foo/secret1");
        GetSecretValueResult result1 = new GetSecretValueResult().withSecretString("{\"foo\":\"secret1\"}");
        GetSecretValueRequest request2 = new GetSecretValueRequest().withSecretId("foo/secret2");
        GetSecretValueResult result2 = new GetSecretValueResult().withSecretString("{\"bar\":\"secret2\"}");

        reset(this.secretsManagerClient);
        expect(this.secretsManagerClient.getSecretValue(request1)).andReturn(result1);
        expect(this.secretsManagerClient.getSecretValue(request2)).andReturn(result2);
        replay(this.secretsManagerClient);

        Collection<String> multipleCoordinates = ImmutableList.of("foo/secret1", "foo/secret2");
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(this.secretsManagerClient, multipleCoordinates);

        Set<Peer> peers = dao.findAll();
        assertEquals(2, peers.size());
        assertTrue(peers.contains(new Peer("foo", "secret1")));
        assertTrue(peers.contains(new Peer("bar", "secret2")));
    }

    @Test
    public void testMockClasspathSupport() {
        Collection<String> secretCoordinates = ImmutableList.of("mock:/fake_allowed_peers.json");
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(this.secretsManagerClient, secretCoordinates);

        Set<Peer> peers = dao.findAll();
        assertEquals(2, peers.size());
        assertTrue(peers.contains(new Peer("mock_user", "some_secret")));
        assertTrue(peers.contains(new Peer("another_user", "another_secret")));
    }

    @Test
    public void testAllMocksDontRequireRealClient() {
        Collection<String> secretCoordinates = ImmutableList.of("mock:/fake_allowed_peers.json");
        AWSSecretsManagerPeerDAO dao = new AWSSecretsManagerPeerDAO(null, secretCoordinates);

        // the test is that the dao constructor doesn't throw an exception when constructed
        // with a null client because all secretCoordinates start with "mock:/"
    }
}
