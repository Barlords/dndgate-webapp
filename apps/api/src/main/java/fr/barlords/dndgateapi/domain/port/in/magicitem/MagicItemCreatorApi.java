package fr.barlords.dndgateapi.domain.port.in.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;

import java.util.UUID;

public interface MagicItemCreatorApi {

    MagicItem create(UUID accountId, MagicItem magicItem);

}
