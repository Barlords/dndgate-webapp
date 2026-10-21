package fr.barlords.dndgateapi.bootstrap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an identifier that appears on each log line it produces ({@code correlationId}) and in the
 * {@code X-Request-Id} header of the answer, so that "the request that failed at 14:02" can be found in the logs.
 * <p>
 * The identifier sent by the caller is kept when it is harmless (the front can follow a call from the page to the API);
 * anything else (line breaks, spaces, markup, very long values) is replaced: it ends up in the logs and in a header.
 * The filter runs first, so that even a request refused by the rate limit or by security carries one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";

    static final String MDC_KEY = "correlationId";

    private static final Pattern HARMLESS = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !HARMLESS.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Asynchronous dispatches and error dispatches keep the same identifier through the MDC of their own thread. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

}
