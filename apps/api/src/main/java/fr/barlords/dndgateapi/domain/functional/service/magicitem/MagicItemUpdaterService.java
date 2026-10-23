package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemUpdaterService implements MagicItemUpdaterApi {

    private final MagicItemPersistenceSpi spi;

    private final MagicItemOwnerVerifierService magicItemOwnerVerifierService;

    @Override
    public MagicItem update(UUID accountId, MagicItem magicItem) {
        MagicItem previous = magicItemOwnerVerifierService.verify(accountId, magicItem.getId());

        return spi.save(previous
                .withName(Objects.requireNonNullElse(magicItem.getName(), previous.getName()))
                .withType(Objects.requireNonNullElse(magicItem.getType(), previous.getType()))
                .withRarity(Objects.requireNonNullElse(magicItem.getRarity(), previous.getRarity()))
                .withRequiresAttunement(Objects.requireNonNullElse(magicItem.getRequiresAttunement(), previous.getRequiresAttunement()))
                .withDescription(Objects.requireNonNullElse(magicItem.getDescription(), previous.getDescription())));
    }
}
