package fr.barlords.dndgateapi.domain.port.in.message;

import fr.barlords.dndgateapi.domain.functional.model.Message;

import java.util.UUID;

public interface MessageCreatorApi {
    Message create(UUID accountId, Message message);
}
