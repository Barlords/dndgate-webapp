package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;

import java.util.List;
import java.util.UUID;

public interface MagicItemPersistenceSpi extends PersistenceSpi<MagicItem, UUID> {

    /** Sorted by name. */
    List<MagicItem> findByOwnerId(UUID ownerId);

    long countByOwnerId(UUID ownerId);
}
