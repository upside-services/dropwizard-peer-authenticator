package com.getupside.dw.auth;

import com.getupside.dw.auth.dao.PeerDAO;
import com.getupside.dw.auth.model.Peer;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>Implementation of a DropWizard Authenticator interface that forces the our callers to authenticate with us via Basic
 * Auth.</p>
 */
public class AllowedPeerAuthenticator implements Authenticator<BasicCredentials, Peer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Set<Peer> allPeers;

    public AllowedPeerAuthenticator(PeerDAO peerDAO) {
        this.allPeers = peerDAO.findAll();
        LOGGER.info("Constructed Authenticator with {} allowed peers", this.allPeers.size());
    }

    @Override
    public Optional<Peer> authenticate(BasicCredentials credentials) throws AuthenticationException {
        Peer peer = new Peer(credentials.getUsername(), credentials.getPassword());

        if (this.allPeers.contains(peer)) {
            LOGGER.debug("{} authenticated and allowed to request service", credentials.getUsername());
            return Optional.of(peer);
        }
        else {
            LOGGER.debug("{} is not known in our list of allowed peers", credentials.getUsername());
        }
        return Optional.empty();
    }

}
