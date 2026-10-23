package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemOwnerVerifierService {

    private final MagicItemPersistenceSpi spi;

    public MagicItem verify(UUID accountId, UUID magicItemId) {
        MagicItem magicItem = spi.findById(magicItemId)
                .orElseThrow(MagicItemNotFoundException::new);

        if (!accountId.equals(magicItem.getOwner().getId())) {
            throw new MagicItemOwnerException();
        }

        return magicItem;
    }

}
