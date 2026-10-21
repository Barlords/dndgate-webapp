package fr.barlords.dndgateapi.domain.port.in.account;

public interface SessionCleanerApi {

    /** Removes the sessions that expired, with their refresh tokens. */
    void purgeExpired();

}
