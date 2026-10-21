package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdventurePersistenceSpi extends PersistenceSpi<Adventure, UUID> {

    List<Adventure> findAllByCreatorId(UUID creatorId);

    List<Adventure> findAllByPlayersId(UUID players);

    /** Public adventures and the ones the account takes part in. */
    Page<Adventure> findVisibleTo(UUID accountId, PageRequest request);

    Page<Adventure> findPageByCreatorId(UUID creatorId, PageRequest request);

    Page<Adventure> findPageByPlayerId(UUID playerId, PageRequest request);

    /** Public adventures that have not started and that the account has not joined yet. */
    Page<Adventure> findAvailableTo(UUID accountId, PageRequest request);

    void deleteById(UUID id);

    Adventure update(Adventure o);

    Optional<Adventure> findByInvitationCode(String code);


    /** Adventures the account is the game master of and that are not over (not started, or ongoing). */
    long countActiveByOwnerId(UUID ownerId);
}
