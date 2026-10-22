package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Every editable row carries a version: a write made from a stale read is refused instead of silently erasing the
 * change made meanwhile, the "read, then modify" operations are replayed, and a new row is inserted without being
 * looked for first.
 */
class OptimisticLockingIntegrationTest extends ApiIntegrationTestSupport {

    private static final int CALLERS = 8;

    @Autowired
    AccountPersistenceSpi accounts;

    @Autowired
    SpellBookPersistenceSpi spellBooks;

    @Autowired
    GameCharacterPersistenceSpi characters;

    @Autowired
    AdventurePersistenceSpi adventures;

    @Autowired
    AdventureNotePersistenceSpi adventureNotes;

    @Autowired
    HistoricalNotePersistenceSpi historicalNotes;

    @Autowired
    PlanningPersistenceSpi plannings;

    @Autowired
    AvailabilityPersistenceSpi availabilities;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private TestAccount master;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
    }

    /** All the calls leave at the same moment; the answers come back in the same order. */
    private List<ResponseEntity<String>> responsesInParallel(List<Callable<ResponseEntity<String>>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> results = new ArrayList<>();
            for (Callable<ResponseEntity<String>> call : calls) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> result : results) {
                responses.add(result.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> inParallel(List<Callable<ResponseEntity<String>>> calls) throws Exception {
        return responsesInParallel(calls).stream().map(response -> response.getStatusCode().value()).toList();
    }

    private UUID createSpellBook() {
        ResponseEntity<String> response = call(HttpMethod.POST, "/spellbooks", master.token(), Map.of("name", "Book", "description", "Spells"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    private List<String> spellsOf(UUID spellBookId) {
        return jdbc.queryForList("select spell from spellbook_spells where spellbook_id = ?", String.class, spellBookId);
    }

    private UUID createPlanning() {
        ResponseEntity<String> response = call(HttpMethod.POST, "/planning", master.token(), Map.of(
                "adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-01", "2027-02-08", "2027-02-15")));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    private ResponseEntity<String> vote(TestAccount account, UUID planningId, String date) {
        return call(HttpMethod.POST, "/planning/availability", account.token(),
                Map.of("planningId", planningId.toString(), "propositionDates", List.of(date)));
    }

    // ---- "read, then modify" operations are replayed, nothing is lost ----

    @Test
    void spells_added_at_the_same_moment_are_all_kept() throws Exception {
        UUID spellBookId = createSpellBook();
        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String spell = "spell-" + i;
            calls.add(() -> call(HttpMethod.POST, "/spellbooks/" + spellBookId + "/spells", master.token(), Map.of("spellIndex", spell)));
        }

        List<Integer> statuses = inParallel(calls);

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 201), statuses.toString());
        Assertions.assertEquals(CALLERS, spellsOf(spellBookId).size(), spellsOf(spellBookId).toString());
    }

    @Test
    void spells_removed_at_the_same_moment_are_all_removed() throws Exception {
        UUID spellBookId = createSpellBook();
        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String spell = "spell-" + i;
            Assertions.assertEquals(201, call(HttpMethod.POST, "/spellbooks/" + spellBookId + "/spells", master.token(),
                    Map.of("spellIndex", spell)).getStatusCode().value());
            calls.add(() -> call(HttpMethod.DELETE, "/spellbooks/" + spellBookId + "/spells/" + spell, master.token(), null));
        }

        List<Integer> statuses = inParallel(calls);

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 204), statuses.toString());
        Assertions.assertEquals(List.of(), spellsOf(spellBookId));
    }

    @Test
    void simultaneous_new_votes_of_one_account_leave_the_dates_of_exactly_one_vote() throws Exception {
        UUID planningId = createPlanning();
        TestAccount player = registerAndLogin();
        Assertions.assertEquals(201, vote(player, planningId, "2027-01-01").getStatusCode().value());
        List<String> sent = new ArrayList<>();
        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String date = "2027-03-0" + (i + 1);
            sent.add(date);
            calls.add(() -> vote(player, planningId, date));
        }

        List<Integer> statuses = inParallel(calls);

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 201), statuses.toString());
        List<String> stored = jdbc.queryForList(
                "select d.availability_dates::text from availability_dates d join availability a on a.id = d.availability_id where a.planning_id = ?",
                String.class, planningId);
        Assertions.assertEquals(1, stored.size(), "the dates of a single vote, not a mix: " + stored);
        Assertions.assertTrue(sent.contains(stored.get(0)), stored.toString());
    }

    /**
     * The creation race is replayed into an update and never reported. Once the planning exists, two updates that
     * cross each other may answer 409 "modified by someone else" (on purpose, not replayed), never the conflict of
     * the unique index.
     */
    @Test
    void plannings_sent_at_the_same_moment_for_one_adventure_make_a_single_planning() throws Exception {
        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            String date = "2027-04-0" + (i + 1);
            calls.add(() -> call(HttpMethod.PUT, "/planning", master.token(),
                    Map.of("adventureId", adventureId.toString(), "propositionDates", List.of(date))));
        }

        List<ResponseEntity<String>> responses = responsesInParallel(calls);

        for (ResponseEntity<String> response : responses) {
            boolean created = response.getStatusCode().value() == 201;
            boolean changedMeanwhile = response.getStatusCode().value() == 409
                    && ConcurrentUpdateException.EXCEPTION_MESSAGE.equals(body(response).get("detail").asString());
            Assertions.assertTrue(created || changedMeanwhile, response.toString());
        }
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from planning where adventure_id = ?", Integer.class, adventureId));
    }

    // ---- a stale write is refused, for every editable row ----

    /** Two copies read at the same moment: the first one saved wins, the second one must not overwrite it. */
    private <T> void assertStaleWriteRefused(Supplier<T> read, UnaryOperator<T> firstChange, UnaryOperator<T> secondChange,
                                             Function<T, T> save) {
        T first = read.get();
        T second = read.get();

        save.apply(firstChange.apply(first));

        Assertions.assertThrows(ConcurrentUpdateException.class, () -> save.apply(secondChange.apply(second)));
    }

    @Test
    void a_stale_account_write_is_refused() {
        assertStaleWriteRefused(() -> accounts.findById(master.id()).orElseThrow(),
                account -> account.withPendingEmail("first@test.fr"),
                account -> account.withPseudo("second" + UUID.randomUUID().toString().substring(0, 8)),
                accounts::save);

        Assertions.assertEquals(master.pseudo(), jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, master.id()));
        Assertions.assertEquals("first@test.fr", jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, master.id()));
    }

    @Test
    void a_stale_spell_book_write_is_refused() {
        UUID spellBookId = createSpellBook();

        assertStaleWriteRefused(() -> spellBooks.findById(spellBookId).orElseThrow(),
                book -> book.withSpellList(new HashSet<>(List.of("fireball"))),
                book -> book.withSpellList(new HashSet<>(List.of("shield"))),
                spellBooks::save);

        Assertions.assertEquals(List.of("fireball"), spellsOf(spellBookId));
    }

    @Test
    void a_stale_character_write_is_refused() {
        ResponseEntity<String> created = createCharacter(master, adventureId, "Hero");
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        UUID characterId = UUID.fromString(body(created).get("id").asString());

        assertStaleWriteRefused(() -> characters.findById(characterId).orElseThrow(),
                character -> character.withName("First"),
                character -> character.withName("Second"),
                characters::save);

        Assertions.assertEquals("First", jdbc.queryForObject("select name from \"character\" where id = ?", String.class, characterId));
    }

    @Test
    void a_stale_adventure_note_write_is_refused() {
        UUID noteId = UUID.randomUUID();
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, 'note', ?, 'PRIVATE')",
                noteId, adventureId, LocalDate.of(2027, 1, 1));

        assertStaleWriteRefused(() -> adventureNotes.findById(noteId).orElseThrow(),
                note -> note.withContent("First"),
                note -> note.withNoteVisibility(NoteVisibility.PUBLIC),
                adventureNotes::save);

        Assertions.assertEquals("PRIVATE", jdbc.queryForObject("select visibility from adventure_note where id = ?", String.class, noteId));
    }

    @Test
    void a_stale_historical_note_write_is_refused() {
        UUID noteId = UUID.randomUUID();
        jdbc.update("insert into historical_note (id, adventure_id, content, date) values (?, ?, 'note', ?)",
                noteId, adventureId, LocalDate.of(2027, 1, 1));

        assertStaleWriteRefused(() -> historicalNotes.findById(noteId).orElseThrow(),
                note -> note.withContent("First"),
                note -> note.withContent("Second"),
                historicalNotes::save);

        Assertions.assertEquals("First", jdbc.queryForObject("select content from historical_note where id = ?", String.class, noteId));
    }

    @Test
    void a_stale_planning_write_is_refused() {
        UUID planningId = createPlanning();

        assertStaleWriteRefused(() -> plannings.findById(planningId).orElseThrow(),
                planning -> planning.withPropositionDates(new ArrayList<>(List.of(LocalDate.of(2027, 5, 1)))),
                planning -> planning.withPropositionDates(new ArrayList<>(List.of(LocalDate.of(2027, 5, 2)))),
                plannings::save);

        Assertions.assertEquals(List.of("2027-05-01"), jdbc.queryForList(
                "select proposition_dates::text from propositions where planning_id = ?", String.class, planningId));
    }

    @Test
    void a_stale_availability_write_is_refused() {
        UUID planningId = createPlanning();
        UUID voteId = UUID.fromString(body(vote(master, planningId, "2027-02-01")).get("id").asString());

        assertStaleWriteRefused(() -> availabilities.findById(voteId).orElseThrow(),
                vote -> vote.withAvailabilityDates(new ArrayList<>(List.of(LocalDate.of(2027, 2, 8)))),
                vote -> vote.withAvailabilityDates(new ArrayList<>(List.of(LocalDate.of(2027, 2, 15)))),
                availabilities::save);

        Assertions.assertEquals(List.of("2027-02-08"), jdbc.queryForList(
                "select availability_dates::text from availability_dates where availability_id = ?", String.class, voteId));
    }

    @Test
    void every_save_moves_the_version_forward() {
        Account read = accounts.findById(master.id()).orElseThrow();

        Account saved = accounts.save(read.withPendingEmail("next@test.fr"));

        Assertions.assertNotNull(read.getVersion());
        Assertions.assertEquals(read.getVersion() + 1, saved.getVersion());
        Assertions.assertEquals(saved.getVersion(), accounts.findById(master.id()).orElseThrow().getVersion());
    }

    // ---- a new row is inserted without being looked for first ----

    private long statementsFor(Runnable save) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        save.run();

        return statistics.getPrepareStatementCount();
    }

    @Test
    void a_new_row_costs_a_single_insert_and_no_select() {
        Account owner = accounts.findById(master.id()).orElseThrow();
        Adventure adventure = adventures.findById(adventureId).orElseThrow();
        UUID planningId = createPlanning();
        UUID otherAdventureId = createAdventure(master, "PUBLIC");

        Assertions.assertEquals(1, statementsFor(() -> accounts.save(Account.builder()
                .email(UUID.randomUUID() + "@test.fr").password("hash").pseudo("new" + UUID.randomUUID().toString().substring(0, 8)).build())), "account");
        Assertions.assertEquals(1, statementsFor(() -> spellBooks.save(SpellBook.builder().owner(owner).name("Book").build())), "spell book");
        Assertions.assertEquals(1, statementsFor(() -> characters.save(GameCharacter.builder().owner(owner).adventure(adventure)
                .name("Hero").race("Elf").martialClass("Bard").personality("Kind").background("Old").picturePath("p.png").build())), "character");
        Assertions.assertEquals(1, statementsFor(() -> adventureNotes.save(AdventureNote.builder().adventure(adventure)
                .content("note").date(LocalDate.of(2027, 1, 1)).noteVisibility(NoteVisibility.PUBLIC).build())), "adventure note");
        Assertions.assertEquals(1, statementsFor(() -> historicalNotes.save(HistoricalNote.builder().adventure(adventure)
                .content("note").date(LocalDate.of(2027, 1, 1)).build())), "historical note");
        Assertions.assertEquals(1, statementsFor(() -> plannings.save(Planning.builder().adventureId(otherAdventureId).build())), "planning");
        Assertions.assertEquals(1, statementsFor(() -> availabilities.save(Availability.builder().planningId(planningId).accountId(owner.getId()).build())),
                "availability");
    }
}
