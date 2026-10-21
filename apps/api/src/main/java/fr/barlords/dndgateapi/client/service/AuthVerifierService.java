package fr.barlords.dndgateapi.client.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Gives access to the identity of the authenticated caller.
 * The token itself has already been verified by Spring Security.
 */
@Service
public class AuthVerifierService {

    /**
     * Session the caller logged in with, carried by its access token.
     */
    public UUID currentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return UUID.fromString(jwt.getClaimAsString("sid"));
    }

    public UUID currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(authentication.getName());
    }

}
