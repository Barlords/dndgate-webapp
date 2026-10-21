package fr.barlords.dndgateapi.domain.functional.exception.planning;

public class PlanningNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Planning not found";

    public PlanningNotFoundException(){ super(EXCEPTION_MESSAGE);}
}
