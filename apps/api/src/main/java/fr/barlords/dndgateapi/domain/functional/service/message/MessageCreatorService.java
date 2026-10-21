package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.MessageRateLimitException;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class MessageCreatorService implements MessageCreatorApi {

    private final MessagePersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final Clock clock;

    private final QuantityLimits limits;

    @Override
    public Message create(UUID accountId, Message message) {
        adventureAccessVerifierApi.verifyParticipant(accountId, message.getAdventureId());

        Instant now = Instant.now(clock);
        Instant since = now.minus(limits.messageWindow());
        if (spi.countBySenderIdSince(accountId, since) >= limits.messagesPerWindow()) {
            // Free again when the oldest message of the window leaves it
            Instant oldest = spi.findOldestSendDateBySenderIdSince(accountId, since).orElse(now);
            throw new MessageRateLimitException(oldest.plus(limits.messageWindow()));
        }

        return spi.save(message.withSenderId(accountId).withSendDate(now))
                .getOrElseThrow(DataNotSaveException::new);
    }

}
