package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

class WebSocketConfigTest {

    /** The registration keeps the limit in a protected getter: this exposes it to the test. */
    private static final class Registration extends WebSocketTransportRegistration {
        int messageSizeLimit() {
            return getMessageSizeLimit();
        }
    }

    @Test
    void a_frame_has_a_size_limit_well_below_the_default_but_above_what_a_client_legitimately_sends() {
        Registration registration = new Registration();

        new WebSocketConfig(null).configureWebSocketTransport(registration);

        // the default is 64 KB; a CONNECT frame carries one JWT (about a kilobyte) and SUBSCRIBE a destination
        Assertions.assertTrue(registration.messageSizeLimit() <= 16 * 1024, "limit: " + registration.messageSizeLimit());
        Assertions.assertTrue(registration.messageSizeLimit() >= 4 * 1024, "limit: " + registration.messageSizeLimit());
    }

}
