package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class MessageCreatorService implements MessageCreatorApi {

    private final MessagePersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public Message create(UUID accountId, Message message) {
        adventureAccessVerifierApi.verifyParticipant(accountId, message.getAdventureId());

        return spi.save(message.withSenderId(accountId))
                .getOrElseThrow(DataNotSaveException::new);
    }

}
