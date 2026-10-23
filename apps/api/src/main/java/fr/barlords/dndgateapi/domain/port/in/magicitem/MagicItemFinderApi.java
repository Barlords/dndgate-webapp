package fr.barlords.dndgateapi.domain.port.in.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MagicItemFinderApi {

    /**
     * A magic item is personal: only its owner can read it.
     */
    Optional<MagicItem> findById(UUID accountId, UUID id);

    List<MagicItem> findMine(UUID accountId);

}
