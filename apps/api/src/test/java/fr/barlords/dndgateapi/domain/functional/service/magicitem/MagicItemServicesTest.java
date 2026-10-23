package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemRarity;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemType;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A magic item is personal: only its owner reads, changes or deletes it, and a deletion retypes its name. */
@ExtendWith(MockitoExtension.class)
class MagicItemServicesTest {

    @Mock
    private MagicItemPersistenceSpi spi;

    @Mock
    private AuditSpi audit;

    private final Account owner = Account.builder().pseudo("owner").email("owner@test.fr").build();

    private final Account stranger = Account.builder().pseudo("stranger").email("stranger@test.fr").build();

    private final MagicItem ring = MagicItem.builder()
            .owner(owner).name("Ring of Protection").type(MagicItemType.RING).rarity(MagicItemRarity.RARE)
            .requiresAttunement(true).description("+1 to AC").version(3L)
            .build();

    private MagicItemOwnerVerifierService verifier() {
        return new MagicItemOwnerVerifierService(spi);
    }

    // ---- reading ----

    @Test
    void the_owner_reads_the_item() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));

        Assertions.assertEquals(Optional.of(ring), new MagicItemFinderService(spi).findById(owner.getId(), ring.getId()));
    }

    @Test
    void someone_else_cannot_read_the_item() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));

        Assertions.assertThrows(MagicItemOwnerException.class,
                () -> new MagicItemFinderService(spi).findById(stranger.getId(), ring.getId()));
    }

    @Test
    void an_unknown_item_is_nothing() {
        UUID unknown = UUID.randomUUID();
        when(spi.findById(unknown)).thenReturn(Optional.empty());

        Assertions.assertTrue(new MagicItemFinderService(spi).findById(owner.getId(), unknown).isEmpty());
    }

    @Test
    void the_list_of_mine_is_the_one_of_the_caller() {
        when(spi.findByOwnerId(owner.getId())).thenReturn(List.of(ring));

        Assertions.assertEquals(List.of(ring), new MagicItemFinderService(spi).findMine(owner.getId()));
    }

    // ---- update ----

    @Test
    void a_patch_changes_what_it_gives_and_keeps_the_rest_and_the_version() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));
        when(spi.save(any(MagicItem.class))).thenAnswer(i -> i.getArguments()[0]);

        MagicItem patched = new MagicItemUpdaterService(spi, verifier()).update(owner.getId(),
                MagicItem.builder().id(ring.getId()).rarity(MagicItemRarity.LEGENDARY).requiresAttunement(false).build());

        Assertions.assertEquals(MagicItemRarity.LEGENDARY, patched.getRarity());
        Assertions.assertEquals(false, patched.getRequiresAttunement());
        Assertions.assertEquals("Ring of Protection", patched.getName());
        Assertions.assertEquals(MagicItemType.RING, patched.getType());
        Assertions.assertEquals("+1 to AC", patched.getDescription());
        Assertions.assertEquals(owner, patched.getOwner());
        Assertions.assertEquals(3L, patched.getVersion());
    }

    @Test
    void an_empty_patch_changes_nothing() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));
        when(spi.save(any(MagicItem.class))).thenAnswer(i -> i.getArguments()[0]);

        MagicItem patched = new MagicItemUpdaterService(spi, verifier())
                .update(owner.getId(), MagicItem.builder().id(ring.getId()).build());

        Assertions.assertEquals(ring, patched);
    }

    @Test
    void someone_else_cannot_change_the_item() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));

        Assertions.assertThrows(MagicItemOwnerException.class, () -> new MagicItemUpdaterService(spi, verifier())
                .update(stranger.getId(), MagicItem.builder().id(ring.getId()).name("Mine now").build()));

        verify(spi, never()).save(any());
    }

    @Test
    void an_unknown_item_cannot_be_changed() {
        UUID unknown = UUID.randomUUID();
        when(spi.findById(unknown)).thenReturn(Optional.empty());

        Assertions.assertThrows(MagicItemNotFoundException.class, () -> new MagicItemUpdaterService(spi, verifier())
                .update(owner.getId(), MagicItem.builder().id(unknown).name("Ghost").build()));
    }

    // ---- deletion ----

    @Test
    void the_owner_deletes_the_item_by_retyping_its_name_and_the_deletion_is_audited() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));

        new MagicItemDeleterService(spi, verifier(), audit).deleteById(owner.getId(), ring.getId(), " Ring of Protection ");

        verify(spi).deleteById(ring.getId());
        verify(audit).deletion("magic-item", owner.getId(), ring.getId());
    }

    @Test
    void a_wrong_name_deletes_nothing() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));
        MagicItemDeleterService service = new MagicItemDeleterService(spi, verifier(), audit);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), ring.getId(), "ring of protection"));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), ring.getId(), null));

        verify(spi, never()).deleteById(any());
        verifyNoInteractions(audit);
    }

    @Test
    void someone_else_cannot_delete_the_item_even_with_its_name() {
        when(spi.findById(ring.getId())).thenReturn(Optional.of(ring));

        Assertions.assertThrows(MagicItemOwnerException.class, () -> new MagicItemDeleterService(spi, verifier(), audit)
                .deleteById(stranger.getId(), ring.getId(), "Ring of Protection"));

        verify(spi, never()).deleteById(any());
    }
}
