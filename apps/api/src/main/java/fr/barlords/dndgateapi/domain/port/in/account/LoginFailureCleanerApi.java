package fr.barlords.dndgateapi.domain.port.in.account;

public interface LoginFailureCleanerApi {

    /** Forgets the failed logins that are too old to count in any lock. */
    void purgeExpired();

}
