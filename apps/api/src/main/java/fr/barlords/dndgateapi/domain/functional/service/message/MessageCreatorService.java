package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.exception.MessageRateLimitException;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
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

    private final EntitlementApi entitlement;

    private final Clock clock;

    private final QuantityLimits limits;

    @Override
    public Message create(UUID accountId, Message message) {
        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, message.getAdventureId());

        Instant now = Instant.now(clock);
        Instant since = now.minus(limits.messageWindow());
        if (spi.countBySenderIdSince(accountId, since) >= limits.messagesPerWindow()) {
            // Free again when the oldest message of the window leaves it
            Instant oldest = spi.findOldestSendDateBySenderIdSince(accountId, since).orElse(now);
            throw new MessageRateLimitException(oldest.plus(limits.messageWindow()));
        }

        Message saved = spi.save(message.withSenderId(accountId).withSendDate(now));
        // The history holds what the plan of the game master allows, even after a downgrade
        spi.deleteOldestBeyond(message.getAdventureId(), entitlement.masterPlanOf(adventure).limit(PlanLimit.MESSAGES_PER_ADVENTURE));
        return saved;
    }

}
