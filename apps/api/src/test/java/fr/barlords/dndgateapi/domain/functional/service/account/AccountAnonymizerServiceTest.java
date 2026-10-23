package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterTransferNotifier;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountAnonymizerServiceTest {
    private static final ClientAddress CLIENT = new ClientAddress("203.0.113.7");


    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private AccountPersistenceSpi accounts;

    @Mock
    private AdventurePersistenceSpi adventures;

    @Mock
    private MasterClaimPersistenceSpi masterClaims;

    @Mock
    private SpellBookPersistenceSpi spellBooks;

    @Mock
    private MagicItemPersistenceSpi magicItems;

    @Mock
    private GameCharacterPersistenceSpi gameCharacters;

    @Mock
    private MinioSpi minio;

    @Mock
    private PasswordHasherSpi passwordHasher;

    @Mock
    private OpaqueTokenSpi opaqueTokens;

    @Mock
    private AccountPasswordVerifierService passwordVerifier;

    @Mock
    private AccountSessionRevokerApi sessionRevoker;

    @Mock
    private MasterTransferNotifier notifier;

    private AccountAnonymizerService service;

    private final Account account = Account.builder()
            .pseudo("gandalf").email("gandalf@middle.earth").password("hash").activated(true)
            .build();

    @Mock
    private AuditSpi audit;

    @Mock
    private LoginFailureSpi loginFailures;

    @BeforeEach
    void setUp() {
        service = new AccountAnonymizerService(accounts, adventures, masterClaims, spellBooks, magicItems, gameCharacters, minio,
                passwordHasher, opaqueTokens,
                passwordVerifier, sessionRevoker, notifier, Clock.fixed(NOW, ZoneOffset.UTC), audit, loginFailures);
        lenient().when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        lenient().when(opaqueTokens.generate()).thenReturn("random-secret");
        lenient().when(passwordHasher.hash("random-secret")).thenReturn("unusable-hash");
        lenient().when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);
        lenient().when(adventures.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);
        lenient().when(adventures.findAllByCreatorId(account.getId())).thenReturn(List.of());
        lenient().when(adventures.findAllByPlayersId(account.getId())).thenReturn(List.of());
        lenient().when(masterClaims.findOpenByCandidateId(account.getId())).thenReturn(List.of());
        lenient().when(masterClaims.save(any(MasterClaim.class))).thenAnswer(i -> i.getArguments()[0]);
        lenient().when(spellBooks.findByOwnerId(account.getId())).thenReturn(List.of());
        lenient().when(magicItems.findByOwnerId(account.getId())).thenReturn(List.of());
        lenient().when(gameCharacters.findByOwnerId(account.getId())).thenReturn(List.of());
    }

    private Account savedAccount() {
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        return saved.getValue();
    }

    private Adventure adventure(AdventureStatus status, UUID owner, UUID... players) {
        return Adventure.builder().ownerId(owner).status(status).visibility(AdventureVisibility.PUBLIC)
                .maxPlayer(4).players(new ArrayList<>(List.of(players))).build();
    }

    // ---- the account itself ----

    @Test
    void an_unknown_account_cannot_be_anonymized() {
        UUID unknown = UUID.randomUUID();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());

        Assertions.assertThrows(AccountNotFoundException.class, () -> service.anonymize(unknown, "password", CLIENT));
        verify(accounts, never()).save(any());
    }

    @Test
    void nothing_that_identifies_the_person_is_kept() {
        service.anonymize(account.getId(), "password", CLIENT);

        Account anonymous = savedAccount();
        Assertions.assertEquals(account.getId(), anonymous.getId());
        Assertions.assertTrue(anonymous.getPseudo().startsWith("deleted-"));
        Assertions.assertFalse(anonymous.getPseudo().contains("gandalf"));
        Assertions.assertTrue(anonymous.getEmail().endsWith("@anonymized.invalid"));
        Assertions.assertFalse(anonymous.getEmail().contains("gandalf"));
        Assertions.assertEquals("unusable-hash", anonymous.getPassword());
        Assertions.assertFalse(anonymous.isActivated());
        Assertions.assertEquals(NOW, anonymous.getAnonymizedAt());
        Assertions.assertTrue(anonymous.isAnonymized());
    }

    @Test
    void an_address_change_waiting_for_its_confirmation_is_forgotten() {
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account
                .withPendingEmail("gandalf@shire.example")
                .withPendingEmailCode("secretcode")
                .withPendingEmailExpiresAt(NOW.plusSeconds(60))));

        service.anonymize(account.getId(), "password", CLIENT);

        Account anonymous = savedAccount();
        Assertions.assertNull(anonymous.getPendingEmail());
        Assertions.assertNull(anonymous.getPendingEmailCode());
        Assertions.assertNull(anonymous.getPendingEmailExpiresAt());
    }

    @Test
    void the_replacement_pseudo_and_email_are_unique_to_each_account() {
        Account other = Account.builder().pseudo("saruman").email("saruman@middle.earth").password("hash").build();
        when(accounts.findById(other.getId())).thenReturn(Optional.of(other));

        service.anonymize(account.getId(), "password", CLIENT);
        service.anonymize(other.getId(), "password", CLIENT);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts, org.mockito.Mockito.times(2)).save(saved.capture());
        Assertions.assertNotEquals(saved.getAllValues().get(0).getPseudo(), saved.getAllValues().get(1).getPseudo());
        Assertions.assertNotEquals(saved.getAllValues().get(0).getEmail(), saved.getAllValues().get(1).getEmail());
    }

    @Test
    void every_session_is_ended() {
        service.anonymize(account.getId(), "password", CLIENT);

        verify(sessionRevoker).logoutAll(account.getId());
    }

    @Test
    void anonymizing_twice_only_ends_the_sessions_again() {
        Account already = account.withAnonymizedAt(NOW.minusSeconds(3600));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(already));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(accounts, never()).save(any());
        verify(sessionRevoker).logoutAll(account.getId());
    }

    // ---- the password is asked again ----

    @Test
    void the_login_failures_of_the_account_are_forgotten() {
        service.anonymize(account.getId(), "password", CLIENT);

        verify(loginFailures).deleteByAccount(account.getId());
    }

    @Test
    void a_wrong_password_deletes_nothing() {
        doThrow(new WrongPasswordException()).when(passwordVerifier).verify(any(), any(), any(), any());

        Assertions.assertThrows(WrongPasswordException.class, () -> service.anonymize(account.getId(), "wrong", CLIENT));

        verify(accounts, never()).save(any());
        verify(adventures, never()).save(any());
        verify(spellBooks, never()).deleteById(any());
        verify(magicItems, never()).deleteById(any());
        verify(gameCharacters, never()).deleteById(any());
        verify(sessionRevoker, never()).logoutAll(any());
    }

    @Test
    void the_password_given_is_the_one_that_is_checked() {
        service.anonymize(account.getId(), "my-password", CLIENT);

        verify(passwordVerifier).verify(org.mockito.ArgumentMatchers.eq(account), org.mockito.ArgumentMatchers.eq("my-password"), org.mockito.ArgumentMatchers.eq(CLIENT), any());
    }

    // ---- spell books ----

    @Test
    void the_personal_spell_books_are_deleted() {
        SpellBook first = SpellBook.builder().owner(account).name("one").build();
        SpellBook second = SpellBook.builder().owner(account).name("two").build();
        when(spellBooks.findByOwnerId(account.getId())).thenReturn(List.of(first, second));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(spellBooks).deleteById(first.getId());
        verify(spellBooks).deleteById(second.getId());
    }

    // ---- magic items and characters ----

    @Test
    void the_personal_magic_items_are_deleted() {
        MagicItem first = MagicItem.builder().owner(account).name("Ring").build();
        MagicItem second = MagicItem.builder().owner(account).name("Wand").build();
        when(magicItems.findByOwnerId(account.getId())).thenReturn(List.of(first, second));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(magicItems).deleteById(first.getId());
        verify(magicItems).deleteById(second.getId());
    }

    @Test
    void a_character_outside_any_adventure_is_deleted_with_its_picture_and_one_of_an_adventure_stays() {
        GameCharacter alone = GameCharacter.builder().owner(account).name("Alone").picturePath("account/alone.png").build();
        GameCharacter played = GameCharacter.builder().owner(account).name("Played").picturePath("account/played.png")
                .adventure(Adventure.builder().name("Quest").build()).build();
        when(gameCharacters.findByOwnerId(account.getId())).thenReturn(List.of(alone, played));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(gameCharacters).deleteById(alone.getId());
        verify(minio).deleteAfterCommit("account/alone.png");
        verify(gameCharacters, never()).deleteById(played.getId());
        verify(minio, never()).deleteAfterCommit("account/played.png");
    }

    // ---- adventures owned by the account ----

    @Test
    void an_owned_adventure_that_has_not_started_is_aborted_and_hidden() {
        Adventure open = adventure(AdventureStatus.NOT_STARTED, account.getId(), UUID.randomUUID());
        when(adventures.findAllByCreatorId(account.getId())).thenReturn(List.of(open));

        service.anonymize(account.getId(), "password", CLIENT);

        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(adventures).save(saved.capture());
        Assertions.assertEquals(AdventureStatus.ABORT, saved.getValue().getStatus());
        Assertions.assertEquals(AdventureVisibility.PRIVATE, saved.getValue().getVisibility());
        Assertions.assertEquals(open.getPlayers(), saved.getValue().getPlayers(), "its players keep their place in the history");
    }

    @Test
    void owned_adventures_that_ended_are_left_untouched() {
        when(adventures.findAllByCreatorId(account.getId())).thenReturn(List.of(
                adventure(AdventureStatus.FINISH, account.getId()),
                adventure(AdventureStatus.ABORT, account.getId())));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(adventures, never()).save(any());
        verify(notifier, never()).masterAccountDeleted(any());
    }

    @Test
    void an_ongoing_adventure_goes_on_but_has_no_game_master_any_more() {
        Adventure ongoing = adventure(AdventureStatus.ONGOING, account.getId(), UUID.randomUUID());
        when(adventures.findAllByCreatorId(account.getId())).thenReturn(List.of(ongoing));

        service.anonymize(account.getId(), "password", CLIENT);

        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(adventures).save(saved.capture());
        Assertions.assertTrue(saved.getValue().isMasterVacant());
        Assertions.assertEquals(AdventureStatus.ONGOING, saved.getValue().getStatus());
        Assertions.assertEquals(ongoing.getPlayers(), saved.getValue().getPlayers());
        verify(notifier).masterAccountDeleted(saved.getValue());
    }

    @Test
    void the_open_votes_of_the_account_as_a_candidate_are_cancelled_and_the_voters_are_told() {
        UUID other = UUID.randomUUID();
        Adventure ongoing = adventure(AdventureStatus.ONGOING, UUID.randomUUID(), account.getId(), other)
                .withMasterVacantSince(NOW);
        MasterClaim claim = MasterClaim.builder().adventureId(ongoing.getId()).candidateId(account.getId())
                .createdAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
        when(masterClaims.findOpenByCandidateId(account.getId())).thenReturn(List.of(claim));
        when(adventures.findById(ongoing.getId())).thenReturn(Optional.of(ongoing));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(masterClaims).save(claim.withStatus(MasterClaimStatus.CANCELLED));
        verify(notifier).claimCancelled(ongoing, account.getId(), List.of(other));
    }

    // ---- adventures the account plays in ----

    @Test
    void the_seat_is_freed_in_adventures_that_have_not_started() {
        UUID other = UUID.randomUUID();
        Adventure open = adventure(AdventureStatus.NOT_STARTED, UUID.randomUUID(), account.getId(), other);
        when(adventures.findAllByPlayersId(account.getId())).thenReturn(List.of(open));

        service.anonymize(account.getId(), "password", CLIENT);

        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(adventures).save(saved.capture());
        Assertions.assertEquals(List.of(other), saved.getValue().getPlayers());
    }

    @Test
    void the_account_stays_among_the_players_of_adventures_that_started_or_ended() {
        when(adventures.findAllByPlayersId(account.getId())).thenReturn(List.of(
                adventure(AdventureStatus.ONGOING, UUID.randomUUID(), account.getId()),
                adventure(AdventureStatus.FINISH, UUID.randomUUID(), account.getId())));

        service.anonymize(account.getId(), "password", CLIENT);

        verify(adventures, never()).save(any());
    }
}
