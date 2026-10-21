package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageFinderApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class MessageFinderService implements MessageFinderApi {

    private final MessagePersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public Optional<Message> findById(UUID id) {
        return spi.findById(id);
    }

    @Override
    public Page<Message> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request) {
        adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        return spi.findByAdventureId(adventureId, request);
    }

    @Override
    public List<Message> findBySenderId(UUID senderId) {
        return spi.findBySenderId(senderId);
    }


}
