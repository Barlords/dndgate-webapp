package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private record Outcome(String responseHeader, String idSeenByTheRequest) {
    }

    private Outcome handle(String incomingHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader("X-Request-Id", incomingHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get("correlationId"));
            }
        });

        return new Outcome(response.getHeader("X-Request-Id"), seen.get());
    }

    @Test
    void a_request_without_an_id_gets_one_that_is_in_the_logs_and_in_the_response() throws Exception {
        Outcome outcome = handle(null);

        Assertions.assertNotNull(outcome.responseHeader());
        Assertions.assertEquals(outcome.responseHeader(), outcome.idSeenByTheRequest());
        Assertions.assertDoesNotThrow(() -> java.util.UUID.fromString(outcome.responseHeader()));
    }

    @Test
    void two_requests_get_two_different_ids() throws Exception {
        Assertions.assertNotEquals(handle(null).responseHeader(), handle(null).responseHeader());
    }

    @Test
    void the_id_given_by_the_caller_is_kept_so_that_a_call_can_be_followed_across_services() throws Exception {
        Outcome outcome = handle("front-7f3a-2026");

        Assertions.assertEquals("front-7f3a-2026", outcome.responseHeader());
        Assertions.assertEquals("front-7f3a-2026", outcome.idSeenByTheRequest());
    }

    @Test
    void an_id_that_could_forge_log_lines_or_headers_is_replaced() throws Exception {
        for (String hostile : new String[]{"x\nINFO fake line", "a b", "<script>", "a".repeat(65), "", "é"}) {
            Outcome outcome = handle(hostile);

            Assertions.assertNotEquals(hostile, outcome.responseHeader(), hostile);
            Assertions.assertDoesNotThrow(() -> java.util.UUID.fromString(outcome.responseHeader()), hostile);
        }
    }

    @Test
    void the_id_does_not_leak_to_the_next_request_handled_by_the_same_thread() throws Exception {
        handle("first");

        Assertions.assertNull(MDC.get("correlationId"));
    }
}
