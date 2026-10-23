package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemFinderApi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemFinderService implements MagicItemFinderApi {

    private final MagicItemPersistenceSpi spi;

    @Override
    public Optional<MagicItem> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(magicItem -> {
            if (!accountId.equals(magicItem.getOwner().getId())) {
                throw new MagicItemOwnerException();
            }
            return magicItem;
        });
    }

    @Override
    public List<MagicItem> findMine(UUID accountId) {
        return spi.findByOwnerId(accountId);
    }

}
