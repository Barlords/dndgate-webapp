package fr.barlords.dndgateapi.bootstrap.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import fr.barlords.dndgateapi.domain.port.in.account.SessionVerifierApi;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.List;

@Configuration
public class SecurityConfig {

    /** The routes that work without a token. The OpenAPI contract is written from this list: one place to change. */
    static final String[] PUBLIC_POST_ROUTES = {"/accounts", "/accounts/login", "/accounts/refresh",
            "/accounts/verify", "/accounts/verification-email", "/accounts/email/confirm"};

    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * The probes and metrics are served on the management port, which is not published by the reverse proxy:
     * whoever reaches it is inside the infrastructure, and the collectors do not hold a token.
     * The matcher only knows the endpoints that are exposed (health, prometheus); on the public port it matches nothing.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Sinon toute erreur serveur serait masquee en 401 par la redirection interne vers /error
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ROUTES).permitAll()
                        // the contract (only served outside production, see springdoc.api-docs.enabled)
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").permitAll()
                        // WebSockets are authenticated at the STOMP level (WebSocketSecurityInterceptor)
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemEntryPoint())
                        .accessDeniedHandler(problemAccessDeniedHandler()))
                .build();
    }

    /** Same body as every other error of the API; the {@code WWW-Authenticate} challenge of the bearer scheme stays. */
    private AuthenticationEntryPoint problemEntryPoint() {
        BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            challenge.commence(request, response, exception);
            ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required");
        };
    }

    private AccessDeniedHandler problemAccessDeniedHandler() {
        return (request, response, exception) ->
                ProblemJson.write(response, HttpStatus.FORBIDDEN, "Access is denied");
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("security.jwt.secret must be at least " + MIN_SECRET_LENGTH + " bytes long");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * A token is only accepted while its session is active, so that logging out,
     * changing the password or deleting the account takes effect immediately.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, SessionVerifierApi sessionVerifierApi) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> sessionIsActive = jwt -> {
            if (isActiveSession(jwt, sessionVerifierApi)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The session is revoked or expired", null));
        };

        // no tolerance on the expiration (the default one is 60 seconds)
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), sessionIsActive));

        return decoder;
    }

    private boolean isActiveSession(Jwt jwt, SessionVerifierApi sessionVerifierApi) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null) {
            return false;
        }
        try {
            return sessionVerifierApi.isActive(UUID.fromString(sessionId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
