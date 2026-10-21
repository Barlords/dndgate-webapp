package fr.barlords.dndgateapi.domain.functional.model.account;

/**
 * Who is calling, as far as the lockout is concerned: an opaque identifier of the client (the network address
 * the web layer resolved). The domain never interprets it, it only tells two callers apart.
 */
public record ClientAddress(String value) {

    public ClientAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A client address cannot be blank");
        }
    }

}
