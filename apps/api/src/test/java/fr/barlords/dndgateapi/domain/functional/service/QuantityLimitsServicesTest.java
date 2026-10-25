package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.MessageRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.message.MessageCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookImporterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellWriterService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/** The technical ceilings that remain for everybody (the quotas by plan are in AccountPlanLimitsTest). */
@ExtendWith(MockitoExtension.class)
class QuantityLimitsServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final QuantityLimits LIMITS = new QuantityLimits(5, 8, WINDOW);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Account account = Account.builder().pseudo("someone").email("someone@test.fr").build();

    @Mock
    private SpellBookPersistenceSpi spellBookSpi;

    @Mock
    private MessagePersistenceSpi messageSpi;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    @Mock
    private EntitlementApi entitlement;

    @Mock
    private SpellBookOwnerVerifierService spellBookOwnerVerifier;

    private static Set<String> spells(int count) {
        return new HashSet<>(IntStream.range(0, count).mapToObj(i -> "spell-" + i).toList());
    }

    @Test
    void an_import_with_too_many_spells_is_refused_and_one_at_the_limit_is_kept() {
        when(currentAccountService.getAccountById(account.getId())).thenReturn(account);
        SpellBookImporterService service = new SpellBookImporterService(spellBookSpi, currentAccountService, LIMITS, entitlement);

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

    // ---- messages per account and per window ----

    private Message sendMessage() {
        UUID adventureId = UUID.randomUUID();
        when(accessVerifier.verifyParticipant(account.getId(), adventureId)).thenReturn(Adventure.builder().build());
        return new MessageCreatorService(messageSpi, accessVerifier, entitlement, clock, LIMITS)
                .create(account.getId(), Message.builder().adventureId(adventureId).content("hello").build());
    }

    @Test
    void messages_below_the_rate_limit_are_saved_and_counted_over_the_last_window() {
        when(messageSpi.countBySenderIdSince(account.getId(), NOW.minus(WINDOW))).thenReturn(7L);
        when(messageSpi.save(any(Message.class))).thenAnswer(i -> i.getArguments()[0]);
        java.util.Map<fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit, Integer> planLimits = new java.util.EnumMap<>(fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit.class);
        java.util.Map<fr.barlords.dndgateapi.domain.functional.model.plan.Feature, Boolean> planFeatures = new java.util.EnumMap<>(fr.barlords.dndgateapi.domain.functional.model.plan.Feature.class);
        for (fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit limit : fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit.values()) {
            planLimits.put(limit, 10);
        }
        for (fr.barlords.dndgateapi.domain.functional.model.plan.Feature feature : fr.barlords.dndgateapi.domain.functional.model.plan.Feature.values()) {
            planFeatures.put(feature, true);
        }
        when(entitlement.masterPlanOf(any(Adventure.class))).thenReturn(
                new fr.barlords.dndgateapi.domain.functional.model.plan.Plan("p", false, java.util.Map.of(), planLimits, planFeatures, null));

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
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(0, 1, WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(1, 0, WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new QuantityLimits(1, 1, Duration.ZERO));
        Assertions.assertEquals(30, QuantityLimits.defaults().messagesPerWindow());
        Assertions.assertEquals(1000, QuantityLimits.defaults().spellsPerSpellBook());
    }
}
