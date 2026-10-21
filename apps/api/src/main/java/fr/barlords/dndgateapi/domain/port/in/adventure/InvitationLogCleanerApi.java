package fr.barlords.dndgateapi.domain.port.in.adventure;

public interface InvitationLogCleanerApi {

    /** Forgets the invitations that are too old to count in any limit. */
    void purgeExpired();

}
