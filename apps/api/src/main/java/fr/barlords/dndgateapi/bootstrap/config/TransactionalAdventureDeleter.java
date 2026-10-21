package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureDeleterApi;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deleting an adventure removes its characters, notes, messages and planning: either everything goes or nothing does.
 */
@RequiredArgsConstructor
public class TransactionalAdventureDeleter implements AdventureDeleterApi {

    private final AdventureDeleterApi delegate;

    @Override
    @Transactional
    public void deleteById(UUID accountId, UUID id, String confirmation) {
        delegate.deleteById(accountId, id, confirmation);
    }

}
