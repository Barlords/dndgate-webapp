package fr.barlords.dndgateapi.domain.functional.model.adventure;

public enum AdventureStatus {
    NOT_STARTED,
    ONGOING,
    ABORT,
    FINISH;

    /**
     * An adventure only moves forward: NOT_STARTED to ONGOING, ONGOING to FINISH, and either of the two living statuses to ABORT.
     * Nothing leaves FINISH or ABORT, nothing goes back to NOT_STARTED. Staying on the same status is not a move.
     */
    public boolean canMoveTo(AdventureStatus target) {
        return switch (this) {
            case NOT_STARTED -> target == ONGOING || target == ABORT;
            case ONGOING -> target == FINISH || target == ABORT;
            case FINISH, ABORT -> false;
        };
    }
}
