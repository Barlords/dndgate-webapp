package fr.barlords.dndgateapi.domain.functional.exception.gamecharacter;

/** The character is not where the operation needs it to be (in the reserve, or in a given adventure). */
public class GameCharacterPlacementException extends RuntimeException {

    public static final String NOT_IN_RESERVE = "The character is already in an adventure: move it to the reserve first";

    public static final String NOT_IN_ADVENTURE = "The character is not in this adventure";

    public static final String NPC_CANNOT_MOVE = "A non-player character stays in its adventure";

    public static final String NOT_AN_NPC = "Only a non-player character has a visibility";

    public GameCharacterPlacementException(String message) {
        super(message);
    }

}
