package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.GameCharacterCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.magicitem.MagicItemCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookImporterService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The account quotas are read from the plan of the account: the entitlement refuses, nothing is created. */
@ExtendWith(MockitoExtension.class)
class AccountPlanLimitsTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private static final long USED = 7L;

    private final Account account = Account.builder().pseudo("someone").email("someone@test.fr").build();

    @Mock
    private EntitlementApi entitlement;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AdventurePersistenceSpi adventureSpi;

    @Mock
    private GameCharacterPersistenceSpi characterSpi;

    @Mock
    private SpellBookPersistenceSpi spellBookSpi;

    @Mock
    private MagicItemPersistenceSpi magicItemSpi;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    @Mock
    private MinioSpi minioSpi;

    @BeforeEach
    void setUp() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
    }

    private void planRefuses(PlanLimit limit) {
        doThrow(new PlanLimitReachedException(limit, 1, "test-tight", "test-paid"))
                .when(entitlement).verifyAccountLimit(account.getId(), limit, USED);
    }

    private GameCharacterCreatorService characterCreator() {
        return new GameCharacterCreatorService(characterSpi, accessVerifier, currentAccountService, minioSpi, entitlement);
    }

    @Test
    void an_account_at_its_plan_limit_cannot_create_an_adventure() {
        when(adventureSpi.countActiveByOwnerId(account.getId())).thenReturn(USED);
        planRefuses(PlanLimit.ACTIVE_ADVENTURES_AS_MASTER);

        Assertions.assertThrows(PlanLimitReachedException.class, () ->
                new AdventureCreatorService(adventureSpi, currentAccountService, entitlement)
                        .create(account.getId(), Adventure.builder().name("Quest").build()));

        verify(adventureSpi, never()).save(any());
    }

    @Test
    void an_account_at_its_plan_limit_cannot_create_a_character_in_its_reserve_and_nothing_is_uploaded() throws Exception {
        when(characterSpi.countByOwnerIdWithoutAdventure(account.getId())).thenReturn(USED);
        planRefuses(PlanLimit.CHARACTERS_WITHOUT_ADVENTURE);

        Assertions.assertThrows(PlanLimitReachedException.class, () ->
                characterCreator().create(account.getId(), null, GameCharacter.builder().name("Gandalf").build(),
                        new ByteArrayInputStream(PNG)));

        verify(characterSpi, never()).save(any());
        verify(minioSpi, never()).uploadFile(any(), any(), any());
    }

    @Test
    void a_character_in_an_adventure_does_not_consult_the_account_quota() {
        Adventure adventure = Adventure.builder().name("Quest").build();
        when(accessVerifier.verifyParticipant(account.getId(), adventure.getId())).thenReturn(adventure);
        when(characterSpi.countByOwnerIdAndAdventureId(account.getId(), adventure.getId())).thenReturn(0L);
        when(characterSpi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);

        characterCreator().create(account.getId(), adventure.getId(), GameCharacter.builder().name("Gandalf").build(),
                new ByteArrayInputStream(PNG));

        verify(entitlement, never()).verifyAccountLimit(any(), any(), anyLong());
    }

    @Test
    void an_account_at_its_plan_limit_cannot_create_a_spell_book() {
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(USED);
        planRefuses(PlanLimit.SPELL_BOOKS);

        Assertions.assertThrows(PlanLimitReachedException.class, () ->
                new SpellBookCreatorService(spellBookSpi, currentAccountService, entitlement)
                        .create(account.getId(), SpellBook.builder().name("Book").build()));

        verify(spellBookSpi, never()).save(any());
    }

    @Test
    void an_account_at_its_plan_limit_cannot_import_a_spell_book() {
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(USED);
        planRefuses(PlanLimit.SPELL_BOOKS);

        Assertions.assertThrows(PlanLimitReachedException.class, () ->
                new SpellBookImporterService(spellBookSpi, currentAccountService, QuantityLimits.defaults(), entitlement)
                        .importSpellBook(account.getId(), SpellBook.builder().name("Book").spellList(Set.of("a")).build()));

        verify(spellBookSpi, never()).save(any());
    }

    @Test
    void an_account_at_its_plan_limit_cannot_create_a_magic_item() {
        when(magicItemSpi.countByOwnerId(account.getId())).thenReturn(USED);
        planRefuses(PlanLimit.MAGIC_ITEMS);

        Assertions.assertThrows(PlanLimitReachedException.class, () ->
                new MagicItemCreatorService(magicItemSpi, currentAccountService, entitlement)
                        .create(account.getId(), MagicItem.builder().name("Ring").build()));

        verify(magicItemSpi, never()).save(any());
    }

    @Test
    void an_account_below_its_plan_limits_creates_what_it_asks_for() {
        when(adventureSpi.countActiveByOwnerId(account.getId())).thenReturn(USED);
        when(adventureSpi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(USED);
        when(spellBookSpi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);
        when(magicItemSpi.countByOwnerId(account.getId())).thenReturn(USED);
        when(magicItemSpi.save(any(MagicItem.class))).thenAnswer(i -> i.getArguments()[0]);
        when(characterSpi.countByOwnerIdWithoutAdventure(account.getId())).thenReturn(USED);
        when(characterSpi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);

        new AdventureCreatorService(adventureSpi, currentAccountService, entitlement)
                .create(account.getId(), Adventure.builder().name("Quest").build());
        new SpellBookCreatorService(spellBookSpi, currentAccountService, entitlement)
                .create(account.getId(), SpellBook.builder().name("Book").build());
        new SpellBookImporterService(spellBookSpi, currentAccountService, QuantityLimits.defaults(), entitlement)
                .importSpellBook(account.getId(), SpellBook.builder().name("Book").spellList(Set.of("a")).build());
        new MagicItemCreatorService(magicItemSpi, currentAccountService, entitlement)
                .create(account.getId(), MagicItem.builder().name("Ring").build());
        characterCreator().create(account.getId(), null, GameCharacter.builder().name("Gandalf").build(),
                new ByteArrayInputStream(PNG));

        verify(entitlement).verifyAccountLimit(account.getId(), PlanLimit.ACTIVE_ADVENTURES_AS_MASTER, USED);
        verify(entitlement, times(2)).verifyAccountLimit(account.getId(), PlanLimit.SPELL_BOOKS, USED);
        verify(entitlement).verifyAccountLimit(account.getId(), PlanLimit.MAGIC_ITEMS, USED);
        verify(entitlement).verifyAccountLimit(account.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, USED);
        verify(entitlement, never()).verifyAccountLimit(any(), eq(PlanLimit.MASTER_NOTES_PER_ADVENTURE), anyLong());
    }
}
