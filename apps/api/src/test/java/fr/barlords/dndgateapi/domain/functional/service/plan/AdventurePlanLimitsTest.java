package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.GameCharacterCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteCreatorService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The adventure quotas are read from the plan of the game master of the adventure: the entitlement is asked with the
 * adventure (not with the account that writes), and when it refuses, nothing is created.
 */
@ExtendWith(MockitoExtension.class)
class AdventurePlanLimitsTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private static final long USED = 7L;

    private final Account account = Account.builder().pseudo("someone").email("someone@test.fr").build();

    private final Adventure adventure = Adventure.builder().name("Quest").build();

    @Mock
    private EntitlementApi entitlement;

    @Mock
    private AdventureNotePersistenceSpi noteSpi;

    @Mock
    private HistoricalNotePersistenceSpi historicalNoteSpi;

    @Mock
    private GameCharacterPersistenceSpi characterSpi;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private MinioSpi minioSpi;

    private void masterPlanRefuses(PlanLimit limit) {
        doThrow(new PlanLimitReachedException(limit, 1, "test-tight", "test-paid"))
                .when(entitlement).verifyAdventureLimit(adventure, limit, USED);
    }

    private AdventureNote createNote() {
        when(ownerVerifier.verify(account.getId(), adventure.getId())).thenReturn(adventure);
        return new AdventureNoteCreatorService(noteSpi, ownerVerifier, entitlement)
                .create(account.getId(), adventure.getId(), AdventureNote.builder().build());
    }

    private HistoricalNote createChronicleEntry() {
        when(ownerVerifier.verify(account.getId(), adventure.getId())).thenReturn(adventure);
        return new HistoricalNoteCreatorService(historicalNoteSpi, ownerVerifier, entitlement)
                .create(account.getId(), adventure.getId(), HistoricalNote.builder().build());
    }

    private GameCharacter createCharacter() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        when(accessVerifier.verifyParticipant(account.getId(), adventure.getId())).thenReturn(adventure);
        return new GameCharacterCreatorService(characterSpi, accessVerifier, currentAccountService, minioSpi, entitlement)
                .create(account.getId(), adventure.getId(), GameCharacter.builder().name("Gandalf").build(),
                        new ByteArrayInputStream(PNG));
    }

    @Test
    void a_note_is_refused_when_the_master_plan_is_full() {
        when(noteSpi.countByAdventureId(adventure.getId())).thenReturn(USED);
        masterPlanRefuses(PlanLimit.MASTER_NOTES_PER_ADVENTURE);

        Assertions.assertThrows(PlanLimitReachedException.class, this::createNote);

        verify(noteSpi, never()).save(any());
    }

    @Test
    void a_chronicle_entry_counts_against_the_master_plan() {
        when(historicalNoteSpi.countByAdventureId(adventure.getId())).thenReturn(USED);
        masterPlanRefuses(PlanLimit.HISTORICAL_NOTES_PER_ADVENTURE);

        Assertions.assertThrows(PlanLimitReachedException.class, this::createChronicleEntry);

        verify(historicalNoteSpi, never()).save(any());
    }

    @Test
    void a_player_character_counts_against_the_master_plan_not_the_player_plan_and_nothing_is_uploaded() throws Exception {
        when(characterSpi.countByOwnerIdAndAdventureId(account.getId(), adventure.getId())).thenReturn(USED);
        masterPlanRefuses(PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE);

        Assertions.assertThrows(PlanLimitReachedException.class, this::createCharacter);

        verify(characterSpi, never()).save(any());
        verify(minioSpi, never()).uploadFile(any(), any(), any());
        verify(entitlement, never()).verifyAccountLimit(any(), any(), anyLong());
    }

    @Test
    void below_the_master_plan_limits_the_notes_and_the_character_are_created() {
        when(noteSpi.countByAdventureId(adventure.getId())).thenReturn(USED);
        when(noteSpi.save(any(AdventureNote.class))).thenAnswer(i -> i.getArguments()[0]);
        when(historicalNoteSpi.countByAdventureId(adventure.getId())).thenReturn(USED);
        when(historicalNoteSpi.save(any(HistoricalNote.class))).thenAnswer(i -> i.getArguments()[0]);
        when(characterSpi.countByOwnerIdAndAdventureId(account.getId(), adventure.getId())).thenReturn(USED);
        when(characterSpi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createNote());
        Assertions.assertNotNull(createChronicleEntry());
        Assertions.assertNotNull(createCharacter());

        verify(entitlement).verifyAdventureLimit(adventure, PlanLimit.MASTER_NOTES_PER_ADVENTURE, USED);
        verify(entitlement).verifyAdventureLimit(adventure, PlanLimit.HISTORICAL_NOTES_PER_ADVENTURE, USED);
        verify(entitlement).verifyAdventureLimit(adventure, PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE, USED);
    }
}
