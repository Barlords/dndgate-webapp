package fr.barlords.dndgateapi.domain.functional.exception.gamecharacter;

public class GameCharacterOwnerException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "You are not the owner of the character";

    public GameCharacterOwnerException() {
        super(EXCEPTION_MESSAGE);
    }

}
