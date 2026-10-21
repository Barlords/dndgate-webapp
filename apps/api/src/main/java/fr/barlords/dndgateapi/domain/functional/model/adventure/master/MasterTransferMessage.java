package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

import java.util.UUID;

/**
 * What an e-mail about the game master says. {@code otherPseudo} is the person the event is about
 * (the candidate, the new master...), null when nobody in particular.
 */
public record MasterTransferMessage(MasterTransferEvent event, UUID adventureId, String adventureName, String otherPseudo) {
}
