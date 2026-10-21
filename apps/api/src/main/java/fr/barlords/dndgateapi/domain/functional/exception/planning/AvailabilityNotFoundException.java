package fr.barlords.dndgateapi.domain.functional.exception.planning;

public class AvailabilityNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Planning not found";

    public AvailabilityNotFoundException(){ super(EXCEPTION_MESSAGE);}
}