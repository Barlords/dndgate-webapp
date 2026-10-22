package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.GameCharacterDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.GameCharacterOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every irreversible deletion asks for a confirmation, checked by the domain.
 */
@ExtendWith(MockitoExtension.class)
public class DeletersConfirmationTest {

    @Mock
    private GameCharacterPersistenceSpi characters;

    @Mock
    private GameCharacterOwnerVerifierService characterOwnerVerifier;

    @Mock
    private MinioSpi minioSpi;

    @Mock
    private SpellBookPersistenceSpi spellBooks;

    @Mock
    private SpellBookOwnerVerifierService spellBookOwnerVerifier;

    @Mock
    private AdventureNotePersistenceSpi adventureNotes;

    @Mock
    private HistoricalNotePersistenceSpi historicalNotes;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AuditSpi audit;

    private final Account owner = Account.builder().pseudo("master").email("master@test.fr").build();

    private final Account stranger = Account.builder().pseudo("stranger").email("stranger@test.fr").build();

    private final Adventure adventure = Adventure.builder().ownerId(owner.getId()).name("Quest").build();

    // ---- characters ----

    @Test
    void a_character_is_only_deleted_when_its_name_is_retyped() throws Exception {
        GameCharacter character = GameCharacter.builder().owner(owner).name("Gandalf").picturePath("account/1/g.png").build();
        when(characterOwnerVerifier.verify(owner.getId(), character.getId())).thenReturn(character);
        GameCharacterDeleterService service = new GameCharacterDeleterService(characters, characterOwnerVerifier, minioSpi, audit);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), character.getId(), "gandalf"));
        verify(characters, never()).deleteById(any());
        verifyNoInteractions(minioSpi);

        service.deleteById(owner.getId(), character.getId(), "Gandalf");
        verify(characters).deleteById(character.getId());
        verify(minioSpi).deleteAfterCommit("account/1/g.png");
    }

    @Test
    void someone_else_cannot_delete_a_character_even_with_its_name() {
        UUID id = UUID.randomUUID();
        doThrow(new GameCharacterOwnerException()).when(characterOwnerVerifier).verify(stranger.getId(), id);
        GameCharacterDeleterService service = new GameCharacterDeleterService(characters, characterOwnerVerifier, minioSpi, audit);

        Assertions.assertThrows(GameCharacterOwnerException.class, () -> service.deleteById(stranger.getId(), id, "Gandalf"));
        verify(characters, never()).deleteById(any());
    }

    // ---- spell books ----

    @Test
    void a_spell_book_is_only_deleted_when_its_name_is_retyped() {
        SpellBook book = SpellBook.builder().owner(owner).name("Fireballs").build();
        when(spellBookOwnerVerifier.verify(owner.getId(), book.getId())).thenReturn(book);
        SpellBookDeleterService service = new SpellBookDeleterService(spellBooks, spellBookOwnerVerifier, audit);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), book.getId(), ""));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), book.getId(), "Fireball"));
        verify(spellBooks, never()).deleteById(any());

        service.deleteById(owner.getId(), book.getId(), "Fireballs");
        verify(spellBooks).deleteById(book.getId());
    }

    @Test
    void someone_else_cannot_delete_a_spell_book_even_with_its_name() {
        UUID id = UUID.randomUUID();
        doThrow(new SpellBookOwnerException()).when(spellBookOwnerVerifier).verify(stranger.getId(), id);
        SpellBookDeleterService service = new SpellBookDeleterService(spellBooks, spellBookOwnerVerifier, audit);

        Assertions.assertThrows(SpellBookOwnerException.class, () -> service.deleteById(stranger.getId(), id, "Fireballs"));
        verify(spellBooks, never()).deleteById(any());
    }

    // ---- notes ----

    @Test
    void a_note_of_the_game_master_needs_an_explicit_confirmation() {
        AdventureNote note = AdventureNote.builder().adventure(adventure).content("secret").build();
        when(currentAccountService.getAccountById(owner.getId())).thenReturn(owner);
        when(adventureNotes.findById(note.getId())).thenReturn(Optional.of(note));
        AdventureNoteDeleterService service = new AdventureNoteDeleterService(adventureNotes, currentAccountService, audit);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), note.getId(), false));
        verify(adventureNotes, never()).deleteById(any());

        service.deleteById(owner.getId(), note.getId(), true);
        verify(adventureNotes).deleteById(note.getId());
    }

    @Test
    void only_the_game_master_can_delete_a_note_even_when_confirmed() {
        AdventureNote note = AdventureNote.builder().adventure(adventure).content("secret").build();
        when(currentAccountService.getAccountById(stranger.getId())).thenReturn(stranger);
        when(adventureNotes.findById(note.getId())).thenReturn(Optional.of(note));
        AdventureNoteDeleterService service = new AdventureNoteDeleterService(adventureNotes, currentAccountService, audit);

        Assertions.assertThrows(AdventureOwnerException.class, () -> service.deleteById(stranger.getId(), note.getId(), true));
        verify(adventureNotes, never()).deleteById(any());
    }

    @Test
    void a_historical_note_needs_an_explicit_confirmation_too() {
        HistoricalNote note = HistoricalNote.builder().adventure(adventure).content("history").build();
        when(currentAccountService.getAccountById(owner.getId())).thenReturn(owner);
        when(historicalNotes.findById(note.getId())).thenReturn(Optional.of(note));
        HistoricalNoteDeleterService service = new HistoricalNoteDeleterService(historicalNotes, currentAccountService, audit);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.deleteById(owner.getId(), note.getId(), false));
        verify(historicalNotes, never()).deleteById(any());

        service.deleteById(owner.getId(), note.getId(), true);
        verify(historicalNotes).deleteById(note.getId());
    }

    @Test
    void only_the_game_master_can_delete_a_historical_note_even_when_confirmed() {
        HistoricalNote note = HistoricalNote.builder().adventure(adventure).content("history").build();
        when(currentAccountService.getAccountById(stranger.getId())).thenReturn(stranger);
        when(historicalNotes.findById(note.getId())).thenReturn(Optional.of(note));
        HistoricalNoteDeleterService service = new HistoricalNoteDeleterService(historicalNotes, currentAccountService, audit);

        Assertions.assertThrows(AdventureOwnerException.class, () -> service.deleteById(stranger.getId(), note.getId(), true));
        verify(historicalNotes, never()).deleteById(any());
    }
}
