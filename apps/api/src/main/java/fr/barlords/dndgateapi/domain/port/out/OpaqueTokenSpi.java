package fr.barlords.dndgateapi.domain.port.out;

public interface OpaqueTokenSpi {

    /** A new unguessable secret. */
    String generate();

    /** Stable one-way hash of a secret, the only form that is stored. */
    String hash(String token);

}
