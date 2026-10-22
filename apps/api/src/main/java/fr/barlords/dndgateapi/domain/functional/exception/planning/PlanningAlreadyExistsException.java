package fr.barlords.dndgateapi.domain.functional.exception.planning;

public class PlanningAlreadyExistsException extends RuntimeException {
    public static final String EXCEPTION_MESSAGE = "This adventure already has a planning";

    public PlanningAlreadyExistsException() {
        super(EXCEPTION_MESSAGE);
    }
}
