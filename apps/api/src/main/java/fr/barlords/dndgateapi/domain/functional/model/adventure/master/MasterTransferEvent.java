package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

/** What the participants of an adventure are told about its game master. */
public enum MasterTransferEvent {
    /** To the players: the game master gave up the role. */
    MASTER_STEPPED_DOWN,
    /** To the players: the game master deleted the account. */
    MASTER_ACCOUNT_DELETED,
    /** To the players who vote: someone asks for the role. */
    CLAIM_OPENED,
    /** To the candidate: a player refused. */
    CLAIM_REJECTED,
    /** To the players who vote: the candidate gave up. */
    CLAIM_CANCELLED,
    /** To the new game master. */
    YOU_ARE_NOW_MASTER,
    /** To the former game master. */
    YOU_ARE_NO_LONGER_MASTER,
    /** To the other players. */
    MASTER_CHANGED
}
