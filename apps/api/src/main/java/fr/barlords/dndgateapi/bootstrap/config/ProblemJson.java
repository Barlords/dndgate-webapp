package fr.barlords.dndgateapi.bootstrap.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an error as a problem details document (RFC 9457) from a servlet filter or a security handler,
 * where the controller advice of the API is out of reach. Same fields as what the advice answers.
 */
final class ProblemJson {

    private ProblemJson() {
    }

    /** {@code detail} must be plain text without quotes or backslashes: it is a constant of the code, never user input. */
    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // the identifier is checked by CorrelationIdFilter (letters, digits, ".", "_", "-"): safe to write as it is
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        String correlation = id == null ? "" : ",\"" + CorrelationIdProblemAdvice.PROPERTY + "\":\"" + id + "\"";
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"" + correlation + "}");
    }
}
