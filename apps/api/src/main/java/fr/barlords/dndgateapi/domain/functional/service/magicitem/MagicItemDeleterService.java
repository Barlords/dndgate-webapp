package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemDeleterService implements MagicItemDeleterApi {

    private final MagicItemPersistenceSpi spi;

    private final MagicItemOwnerVerifierService magicItemOwnerVerifierService;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, String confirmation) {
        MagicItem magicItem = magicItemOwnerVerifierService.verify(accountId, id);

        DeletionConfirmation.requireName(magicItem.getName(), confirmation);

        spi.deleteById(id);
        audit.deletion("magic-item", accountId, id);
    }
}
