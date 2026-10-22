package fr.barlords.dndgateapi.domain.port.in.account;

public interface SignupNoticeCleanerApi {

    /** Forgets the notices that are too old to count in the limit. */
    void purgeExpired();

}
