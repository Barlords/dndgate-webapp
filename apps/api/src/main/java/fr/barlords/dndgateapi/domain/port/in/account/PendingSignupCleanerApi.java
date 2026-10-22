package fr.barlords.dndgateapi.domain.port.in.account;

public interface PendingSignupCleanerApi {

    /** Forgets the pending signups whose code expired. */
    void purgeExpired();

}
