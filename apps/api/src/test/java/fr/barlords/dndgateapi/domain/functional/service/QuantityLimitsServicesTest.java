package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.MessageRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.GameCharacterCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.magicitem.MagicItemCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.message.MessageCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookImporterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellWriterService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every cap of review v2 point 40, with small limits: the domain refuses, whatever the caller. */
@ExtendWith(MockitoExtension.class)
class QuantityLimitsServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private static final QuantityLimits LIMITS = new QuantityLimits(2, 3, 4, 5, 6, 7, 9, 10, 8, WINDOW);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Account account = Account.builder().pseudo("someone").email("someone@test.fr").build();

    @Mock
    private AdventurePersistenceSpi adventureSpi;

    @Mock
    private GameCharacterPersistenceSpi characterSpi;

    @Mock
    private SpellBookPersistenceSpi spellBookSpi;

    @Mock
    private MagicItemPersistenceSpi magicItemSpi;

    @Mock
    private AdventureNotePersistenceSpi adventureNoteSpi;

    @Mock
    private HistoricalNotePersistenceSpi historicalNoteSpi;

    @Mock
    private MessagePersistenceSpi messageSpi;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    @Mock
    private AdventureOwnerVerifierService adventureOwnerVerifier;

    @Mock
    private SpellBookOwnerVerifierService spellBookOwnerVerifier;

    @Mock
    private MinioSpi minioSpi;

    private static Set<String> spells(int count) {
        return new HashSet<>(IntStream.range(0, count).mapToObj(i -> "spell-" + i).toList());
    }

    // ---- characters: per player and per adventure ----

    private GameCharacter createCharacter(Adventure adventure) {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        when(accessVerifier.verifyParticipant(account.getId(), adventure.getId())).thenReturn(adventure);
        return new GameCharacterCreatorService(characterSpi, accessVerifier, currentAccountService, minioSpi, LIMITS)
                .create(account.getId(), adventure.getId(), GameCharacter.builder().name("Gandalf").build(), new ByteArrayInputStream(PNG));
    }

    @Test
    void a_player_below_the_character_limit_creates_one() {
        Adventure adventure = Adventure.builder().name("Quest").build();
        when(characterSpi.countByOwnerIdAndAdventureId(account.getId(), adventure.getId())).thenReturn(1L);
        when(characterSpi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createCharacter(adventure));
    }

    @Test
    void a_player_at_the_character_limit_creates_none_and_nothing_is_uploaded() throws Exception {
        Adventure adventure = Adventure.builder().name("Quest").build();
        when(characterSpi.countByOwnerIdAndAdventureId(account.getId(), adventure.getId())).thenReturn(2L);

        Assertions.assertThrows(QuantityLimitExceededException.class, () -> createCharacter(adventure));

        verify(characterSpi, never()).save(any());
        verify(minioSpi, never()).uploadFile(any(), any(), any());
    }

    // ---- characters outside any adventure: per account ----

    private GameCharacter createCharacterWithoutAdventure() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        return new GameCharacterCreatorService(characterSpi, accessVerifier, currentAccountService, minioSpi, LIMITS)
                .create(account.getId(), null, GameCharacter.builder().name("Gandalf").build(), new ByteArrayInputStream(PNG));
    }

    @Test
    void an_account_below_the_limit_of_characters_outside_any_adventure_creates_one() {
        when(characterSpi.countByOwnerIdWithoutAdventure(account.getId())).thenReturn(8L);
        when(characterSpi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createCharacterWithoutAdventure());
        verify(characterSpi, never()).countByOwnerIdAndAdventureId(any(), any());
    }

    @Test
    void an_account_at_the_limit_of_characters_outside_any_adventure_creates_none_and_nothing_is_uploaded() throws Exception {
        when(characterSpi.countByOwnerIdWithoutAdventure(account.getId())).thenReturn(9L);

        QuantityLimitExceededException refused =
                Assertions.assertThrows(QuantityLimitExceededException.class, this::createCharacterWithoutAdventure);

        Assertions.assertEquals("An account can have at most 9 characters outside any adventure", refused.getMessage());
        verify(characterSpi, never()).save(any());
        verify(minioSpi, never()).uploadFile(any(), any(), any());
    }

    // ---- magic items per account ----

    private MagicItem createMagicItem() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        return new MagicItemCreatorService(magicItemSpi, currentAccountService, LIMITS)
                .create(account.getId(), MagicItem.builder().name("Ring").build());
    }

    @Test
    void an_account_below_the_magic_item_limit_creates_one() {
        when(magicItemSpi.countByOwnerId(account.getId())).thenReturn(9L);
        when(magicItemSpi.save(any(MagicItem.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertEquals(account, createMagicItem().getOwner());
    }

    @Test
    void an_account_at_the_magic_item_limit_creates_none() {
        when(magicItemSpi.countByOwnerId(account.getId())).thenReturn(10L);

        QuantityLimitExceededException refused = Assertions.assertThrows(QuantityLimitExceededException.class, this::createMagicItem);

        Assertions.assertEquals("An account can have at most 10 magic items", refused.getMessage());
        verify(magicItemSpi, never()).save(any());
    }

    // ---- active adventures per master ----

    private Adventure createAdventure() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        return new AdventureCreatorService(adventureSpi, currentAccountService, LIMITS)
                .create(account.getId(), Adventure.builder().name("Quest").build());
    }

    @Test
    void a_master_below_the_active_adventure_limit_creates_one() {
        when(adventureSpi.countActiveByOwnerId(account.getId())).thenReturn(2L);
        when(adventureSpi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createAdventure());
    }

    @Test
    void a_master_at_the_active_adventure_limit_creates_none() {
        when(adventureSpi.countActiveByOwnerId(account.getId())).thenReturn(3L);

        Assertions.assertThrows(QuantityLimitExceededException.class, this::createAdventure);

        verify(adventureSpi, never()).save(any());
    }

    // ---- spell books per account ----

    @Test
    void an_account_below_the_spell_book_limit_creates_one() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(3L);
        when(spellBookSpi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(new SpellBookCreatorService(spellBookSpi, currentAccountService, LIMITS)
                .create(account.getId(), SpellBook.builder().name("Book").build()));
    }

    @Test
    void an_account_at_the_spell_book_limit_creates_none() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(4L);

        Assertions.assertThrows(QuantityLimitExceededException.class,
                () -> new SpellBookCreatorService(spellBookSpi, currentAccountService, LIMITS)
                        .create(account.getId(), SpellBook.builder().name("Book").build()));

        verify(spellBookSpi, never()).save(any());
    }

    @Test
    void an_import_counts_as_a_spell_book() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        when(spellBookSpi.countByOwnerId(account.getId())).thenReturn(4L);

        Assertions.assertThrows(QuantityLimitExceededException.class,
                () -> new SpellBookImporterService(spellBookSpi, currentAccountService, LIMITS)
                        .importSpellBook(account.getId(), SpellBook.builder().name("Book").spellList(Set.of("a")).build()));

        verify(spellBookSpi, never()).save(any());
    }

    @Test
    void an_import_with_too_many_spells_is_refused_and_one_at_the_limit_is_kept() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        SpellBookImporterService service = new SpellBookImporterService(spellBookSpi, currentAccountService, LIMITS);

        Assertions.assertThrows(QuantityLimitExceededException.class,
                () -> service.importSpellBook(account.getId(), SpellBook.builder().name("Book").spellList(spells(6)).build()));
        verify(spellBookSpi, never()).save(any());

        when(spellBookSpi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);
        Assertions.assertEquals(5, service.importSpellBook(account.getId(), SpellBook.builder().name("Book").spellList(spells(5)).build())
                .getSpellList().size());
    }

    // ---- spells per spell book ----

    private SpellBook addSpell(Set<String> existing, String added) {
        SpellBook book = SpellBook.builder().owner(account).name("Book").spellList(existing).build();
        when(spellBookOwnerVerifier.verify(account.getId(), book.getId())).thenReturn(book);
        return new SpellWriterService(spellBookSpi, spellBookOwnerVerifier, LIMITS).addSpell(account.getId(), book.getId(), added);
    }

    @Test
    void a_spell_is_added_below_the_limit() {
        when(spellBookSpi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertEquals(5, addSpell(spells(4), "new").getSpellList().size());
    }

    @Test
    void a_new_spell_is_refused_in_a_full_spell_book() {
        Assertions.assertThrows(QuantityLimitExceededException.class, () -> addSpell(spells(5), "new"));

        verify(spellBookSpi, never()).save(any());
    }

    @Test
    void a_spell_already_in_a_full_spell_book_is_not_an_error() {
        when(spellBookSpi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertEquals(5, addSpell(spells(5), "spell-0").getSpellList().size());
    }

    // ---- notes per adventure ----

    private AdventureNote createAdventureNote() {
        Adventure adventure = Adventure.builder().name("Quest").build();
        when(adventureOwnerVerifier.verify(account.getId(), adventure.getId())).thenReturn(adventure);
        return new AdventureNoteCreatorService(adventureNoteSpi, adventureOwnerVerifier, LIMITS)
                .create(account.getId(), adventure.getId(), AdventureNote.builder().build());
    }

    @Test
    void an_adventure_below_the_note_limit_gets_a_note() {
        when(adventureNoteSpi.countByAdventureId(any())).thenReturn(5L);
        when(adventureNoteSpi.save(any(AdventureNote.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createAdventureNote());
    }

    @Test
    void an_adventure_at_the_note_limit_gets_no_note() {
        when(adventureNoteSpi.countByAdventureId(any())).thenReturn(6L);

        Assertions.assertThrows(QuantityLimitExceededException.class, this::createAdventureNote);

        verify(adventureNoteSpi, never()).save(any());
    }

    private HistoricalNote createHistoricalNote() {
        Adventure adventure = Adventure.builder().name("Quest").build();
        when(adventureOwnerVerifier.verify(account.getId(), adventure.getId())).thenReturn(adventure);
        return new HistoricalNoteCreatorService(historicalNoteSpi, adventureOwnerVerifier, LIMITS)
                .create(account.getId(), adventure.getId(), HistoricalNote.builder().build());
    }

    @Test
    void an_adventure_below_the_historical_note_limit_gets_a_note() {
        when(historicalNoteSpi.countByAdventureId(any())).thenReturn(6L);
        when(historicalNoteSpi.save(any(HistoricalNote.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(createHistoricalNote());
    }

    @Test
    void an_adventure_at_the_historical_note_limit_gets_no_note() {
        when(historicalNoteSpi.countByAdventureId(any())).thenReturn(7L);

        Assertions.assertThrows(QuantityLimitExceededException.class, this::createHistoricalNote);

        verify(historicalNoteSpi, never()).save(any());
    }

    // ---- messages per account and per window ----

    private Message sendMessage() {
        UUID adventureId = UUID.randomUUID();
        when(accessVerifier.verifyParticipant(account.getId(), adventureId)).thenReturn(Adventure.builder().build());
        return new MessageCreatorService(messageSpi, accessVerifier, clock, LIMITS)
                .create(account.getId(), Message.builder().adventureId(adventureId).content("hello").build());
    }

    @Test
    void messages_below_the_rate_limit_are_saved_and_counted_over_the_last_window() {
        when(messageSpi.countBySenderIdSince(account.getId(), NOW.minus(WINDOW))).thenReturn(7L);
        when(messageSpi.save(any(Message.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertNotNull(sendMessage());
    }

    @Test
    void messages_at_the_rate_limit_are_refused_until_the_oldest_leaves_the_window() {
        Instant oldest = NOW.minusSeconds(40);
        when(messageSpi.countBySenderIdSince(account.getId(), NOW.minus(WINDOW))).thenReturn(8L);
        when(messageSpi.findOldestSendDateBySenderIdSince(account.getId(), NOW.minus(WINDOW))).thenReturn(Optional.of(oldest));

        MessageRateLimitException refused = Assertions.assertThrows(MessageRateLimitException.class, this::sendMessage);

        Assertions.assertEquals(oldest.plus(WINDOW), refused.getRetryAt());
        verify(messageSpi, never()).save(any());
    }

    @Test
    void the_limits_must_be_positive() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(0, 1, 1, 1, 1, 1, 1, 1, 1, WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(1, 1, 1, 1, 1, 1, 0, 1, 1, WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(1, 1, 1, 1, 1, 1, 1, 0, 1, WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(1, 1, 1, 1, 1, 1, 1, 1, 1, Duration.ZERO));
        Assertions.assertEquals(30, QuantityLimits.defaults().messagesPerWindow());
    }
}
