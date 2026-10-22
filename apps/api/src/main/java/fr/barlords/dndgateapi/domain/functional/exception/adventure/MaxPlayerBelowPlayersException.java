package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class MaxPlayerBelowPlayersException extends RuntimeException {

    public MaxPlayerBelowPlayersException(int maxPlayer, int players) {
        super("The maximum number of players (" + maxPlayer + ") cannot be below the current number of players (" + players + ")");
    }

}
