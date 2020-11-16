package com.getupside.dw.auth;

import com.google.common.collect.ImmutableSet;
import com.getupside.dw.auth.dao.PeerDAO;
import com.getupside.dw.auth.model.Peer;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Optional;
import java.util.Set;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * <p>Tests the authenticator lets Peers in (or keeps 'em out) as appropriate</p>
 */
public class TestAllowedPeerAuthenticator {

    private final Set<Peer> allPeers = ImmutableSet.of(new Peer("foo", "secret1"),
                                                       new Peer("bar", "secret2"));
    private PeerDAO peerDAO;
    private AllowedPeerAuthenticator authenticator;

    @Before
    public void setUp() {
        peerDAO = createNiceMock(PeerDAO.class);
        expect(peerDAO.findAll()).andReturn(allPeers).anyTimes();
        replay(peerDAO);

        authenticator = new AllowedPeerAuthenticator(peerDAO);
    }

    @Test
    public void testPeerIsAllowed() throws AuthenticationException {
        Optional<Peer> peer = authenticator.authenticate(new BasicCredentials("foo", "secret1"));
        assertTrue(peer.isPresent());
        assertEquals(peer.get(), (new Peer("foo", "secret1")));
    }

    @Test
    public void testPeerIsNotAllowed() throws AuthenticationException {
        Optional<Peer> peer = authenticator.authenticate(new BasicCredentials("foo", "secret2"));
        assertFalse(peer.isPresent());
    }
}
