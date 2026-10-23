package fr.barlords.dndgateapi.domain.port.in.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;

import java.util.UUID;

public interface MagicItemUpdaterApi {

    /** Only the owner changes the item; the fields left null keep their value. */
    MagicItem update(UUID accountId, MagicItem magicItem);

}
