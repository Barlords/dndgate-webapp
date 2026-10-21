package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class PlayersAlreadyInAdventureException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Player already in group";

    public PlayersAlreadyInAdventureException() {
        super(EXCEPTION_MESSAGE);
    }

}
