package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saving a message and trimming the history to the limit of the plan happen together: either both do or neither does.
 */
@RequiredArgsConstructor
public class TransactionalMessageCreator implements MessageCreatorApi {

    private final MessageCreatorApi delegate;

    @Override
    @Transactional
    public Message create(UUID accountId, Message message) {
        return delegate.create(accountId, message);
    }

}
