package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class PlayerNotInAdventureException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Player not in group";

    public PlayerNotInAdventureException() {
        super(EXCEPTION_MESSAGE);
    }
}
