package fr.barlords.dndgateapi.domain.port.out;

import java.time.Instant;

/** Memory of the notices sent to owners of addresses somebody tried to register with. */
public interface SignupNoticeLogSpi {

    /**
     * Atomically claims the right to send a notice to the address: true (and the moment is remembered) when none was
     * sent since {@code windowStart}, false otherwise. Two simultaneous calls cannot both get true.
     */
    boolean claim(String email, Instant now, Instant windowStart);

    /** Forgets the notices sent before the given instant. */
    void deleteSentBefore(Instant before);

}
