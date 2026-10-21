package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccessTokenSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtAccessTokenAdapter implements AccessTokenSpi {

    /** Id of the session the token belongs to, checked on every request. */
    public static final String SESSION_CLAIM = "sid";

    private final JwtEncoder jwtEncoder;

    private final Duration timeToLive;

    private final Clock clock;

    public JwtAccessTokenAdapter(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${security.jwt.ttl:PT15M}") Duration timeToLive
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    @Override
    public AccessToken issueFor(Account account, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(timeToLive);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(account.getId().toString())
                .claim(SESSION_CLAIM, sessionId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        String value = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();

        return new AccessToken(value, expiresAt);
    }

}
