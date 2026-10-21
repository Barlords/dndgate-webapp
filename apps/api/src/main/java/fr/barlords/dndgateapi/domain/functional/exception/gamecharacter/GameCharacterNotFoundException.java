package fr.barlords.dndgateapi.domain.functional.exception.gamecharacter;

public class GameCharacterNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Character not found";

    public GameCharacterNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
