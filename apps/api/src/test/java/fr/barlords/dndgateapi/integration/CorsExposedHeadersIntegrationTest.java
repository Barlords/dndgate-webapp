package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

/**
 * A page served from another origin (the front on localhost:4200) can only read the response headers the API exposes.
 */
class CorsExposedHeadersIntegrationTest extends ApiIntegrationTestSupport {

    private static final String ORIGIN = "http://localhost:4200";

    private List<String> exposedBy(ResponseEntity<String> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        Assertions.assertNotNull(header, "no Access-Control-Expose-Headers on the response");
        return Arrays.stream(header.split(",")).map(String::trim).toList();
    }

    @Test
    void a_cross_origin_response_exposes_the_headers_the_front_needs() {
        ResponseEntity<String> response = client().get().uri("/accounts/me")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .retrieve().toEntity(String.class);

        Assertions.assertEquals(ORIGIN, response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        List<String> exposed = exposedBy(response);
        Assertions.assertTrue(exposed.containsAll(List.of("X-Request-Id", "Retry-After", "Content-Disposition")), exposed.toString());
    }

    @Test
    void the_preflight_allows_only_the_request_headers_the_api_reads() {
        ResponseEntity<String> allowed = client().options().uri("/adventures")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-request-id")
                .retrieve().toEntity(String.class);
        ResponseEntity<String> refused = client().options().uri("/adventures")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-unknown-header")
                .retrieve().toEntity(String.class);

        Assertions.assertEquals(200, allowed.getStatusCode().value());
        Assertions.assertEquals(403, refused.getStatusCode().value());
    }
}
