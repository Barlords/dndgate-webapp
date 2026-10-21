package fr.barlords.dndgateapi.bootstrap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Slows down the endpoints that can be abused without being logged in:
 * password guessing, refresh token guessing, account creation (which sends an e-mail)
 * and verification code guessing. The limit is per client address.
 * <p>
 * Behind a reverse proxy, set server.forward-headers-strategy=native so that the address
 * is the one of the client and not the one of the proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, HttpMethod method, String path, int max, Duration window) {
    }

    private final RateLimiter rateLimiter;

    private final List<Rule> rules;

    public RateLimitFilter(
            Clock clock,
            @Value("${security.rate-limit.login.max:10}") int loginMax,
            @Value("${security.rate-limit.login.window:PT1M}") Duration loginWindow,
            @Value("${security.rate-limit.refresh.max:30}") int refreshMax,
            @Value("${security.rate-limit.refresh.window:PT1M}") Duration refreshWindow,
            @Value("${security.rate-limit.register.max:5}") int registerMax,
            @Value("${security.rate-limit.register.window:PT1H}") Duration registerWindow,
            @Value("${security.rate-limit.verify.max:20}") int verifyMax,
            @Value("${security.rate-limit.verify.window:PT1M}") Duration verifyWindow,
            @Value("${security.rate-limit.resend.max:5}") int resendMax,
            @Value("${security.rate-limit.resend.window:PT1H}") Duration resendWindow
    ) {
        this.rateLimiter = new RateLimiter(clock);
        this.rules = List.of(
                new Rule("login", HttpMethod.POST, "/accounts/login", loginMax, loginWindow),
                new Rule("refresh", HttpMethod.POST, "/accounts/refresh", refreshMax, refreshWindow),
                new Rule("register", HttpMethod.POST, "/accounts", registerMax, registerWindow),
                new Rule("verify", HttpMethod.POST, "/accounts/verify", verifyMax, verifyWindow),
                new Rule("resend", HttpMethod.POST, "/accounts/verification-email", resendMax, resendWindow),
                // guessing the code of an address change is as bad as guessing the registration code
                new Rule("confirm-email", HttpMethod.POST, "/accounts/email/confirm", verifyMax, verifyWindow));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        for (Rule rule : rules) {
            if (rule.method().matches(request.getMethod()) && rule.path().equals(path)) {
                long retryAfter = rateLimiter.tryAcquire(rule.name() + ":" + request.getRemoteAddr(), rule.max(), rule.window());
                if (retryAfter > 0) {
                    reject(response, retryAfter);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        ProblemJson.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later.");
    }

}
