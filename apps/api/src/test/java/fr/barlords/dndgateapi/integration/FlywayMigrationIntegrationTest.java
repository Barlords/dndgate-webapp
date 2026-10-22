package fr.barlords.dndgateapi.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The schema is only changed by Flyway, on databases that already hold data. These tests migrate real
 * PostgreSQL databases the way a deployment does: from nothing, and from an older version with rows in it.
 * No Spring context here: only the scripts of {@code db/migration} are exercised.
 */
class FlywayMigrationIntegrationTest {

    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static {
        postgres.start();
    }

    /** A new empty database for each test, so that tests never see each other's rows. */
    private static String newDatabase() throws SQLException {
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
    }

    private static Flyway flyway(String url, String targetVersion) {
        var configuration = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (targetVersion != null) {
            configuration.target(targetVersion);
        }
        return configuration.load();
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    private static long count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void insertAccount(Connection connection, UUID id, String pseudo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into accounts (id, email, password, pseudo, activated) values (?, ?, 'hash', ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, pseudo.toLowerCase() + "-" + id + "@test.fr");
            statement.setString(3, pseudo);
            statement.executeUpdate();
        }
    }

    private static void insertAdventure(Connection connection, UUID id, UUID creator, String visibility, String invitationCode) throws SQLException {
        execute(connection, "insert into adventure (id, creator_id, name, description, max_player, status, visibility, invitation_code) "
                + "values (?, ?, 'Quest', 'A quest', 4, 'NOT_STARTED', ?, ?)", id, creator, visibility, invitationCode);
    }

    private static void insertPlanning(Connection connection, UUID id, UUID adventure) throws SQLException {
        execute(connection, "insert into planning (id, adventure_id) values (?, ?)", id, adventure);
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    // ---- from nothing ----

    @Test
    void an_empty_database_gets_every_migration_in_order_without_error() throws Exception {
        String url = newDatabase();
        Flyway flyway = flyway(url, null);

        flyway.migrate();

        List<MigrationInfo> all = Arrays.asList(flyway.info().all());
        Assertions.assertFalse(all.isEmpty());
        for (MigrationInfo migration : all) {
            Assertions.assertEquals(MigrationState.SUCCESS, migration.getState(), "migration " + migration.getVersion());
        }
        Assertions.assertEquals(0, flyway.info().pending().length);
        Assertions.assertDoesNotThrow(flyway::validate);
        // the versions are consecutive: a gap would mean a script was lost
        for (int i = 0; i < all.size(); i++) {
            Assertions.assertEquals(String.valueOf(i + 1), all.get(i).getVersion().getVersion());
        }
    }

    @Test
    void migrating_twice_changes_nothing_the_second_time() throws Exception {
        String url = newDatabase();
        flyway(url, null).migrate();

        Assertions.assertEquals(0, flyway(url, null).migrate().migrationsExecuted);
    }

    // ---- from an older version, with data ----

    @Test
    void a_database_with_data_keeps_all_of_it_when_it_is_brought_up_to_date() throws Exception {
        String url = newDatabase();
        flyway(url, "1").migrate();

        UUID master = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        try (Connection connection = connect(url)) {
            insertAccount(connection, master, "Master");
            insertAccount(connection, player, "Player");
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into adventure (id, creator_id, name, description, max_player, status, visibility) "
                            + "values (?, ?, 'Quest', 'A quest', 4, 'NOT_STARTED', 'PUBLIC')")) {
                statement.setObject(1, adventure);
                statement.setObject(2, master);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into adventure_players (adventure_id, player_id) values (?, ?)")) {
                statement.setObject(1, adventure);
                statement.setObject(2, player);
                statement.executeUpdate();
            }
        }

        flyway(url, null).migrate();

        try (Connection connection = connect(url)) {
            Assertions.assertEquals(2, count(connection, "select count(*) from accounts"));
            Assertions.assertEquals(1, count(connection, "select count(*) from adventure where id = ? and name = 'Quest' and creator_id = ?", adventure, master));
            Assertions.assertEquals(1, count(connection, "select count(*) from adventure_players where adventure_id = ? and player_id = ?", adventure, player));
            // what a later migration adds starts empty for existing rows
            Assertions.assertEquals(1, count(connection, "select count(*) from adventure where id = ? and master_vacant_since is null", adventure));
        }
    }

    @Test
    void a_pseudo_is_unique_whatever_its_case_once_up_to_date() throws Exception {
        String url = newDatabase();
        flyway(url, null).migrate();

        try (Connection connection = connect(url)) {
            insertAccount(connection, UUID.randomUUID(), "Gandalf");

            Assertions.assertThrows(SQLException.class, () -> insertAccount(connection, UUID.randomUUID(), "gandalf"));
        }
    }

    @Test
    void duplicate_availability_votes_are_reduced_to_one_per_account_and_planning() throws Exception {
        String url = newDatabase();
        flyway(url, "5").migrate();
        UUID planning = UUID.randomUUID();
        UUID otherPlanning = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        try (Connection connection = connect(url)) {
            insertAccount(connection, account, "Voter");
            insertAccount(connection, otherAccount, "OtherVoter");
            UUID adventure = UUID.randomUUID();
            UUID otherAdventure = UUID.randomUUID();
            insertAdventure(connection, adventure, account, "PUBLIC", null);
            insertAdventure(connection, otherAdventure, account, "PUBLIC", null);
            insertPlanning(connection, planning, adventure);
            insertPlanning(connection, otherPlanning, otherAdventure);
            for (UUID[] vote : new UUID[][]{
                    {planning, account}, {planning, account}, {planning, account},
                    {planning, otherAccount}, {otherPlanning, account}}) {
                UUID id = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(
                        "insert into availability (id, planning_id, account_id) values (?, ?, ?)")) {
                    statement.setObject(1, id);
                    statement.setObject(2, vote[0]);
                    statement.setObject(3, vote[1]);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "insert into availability_dates (availability_id, availability_dates) values (?, '2027-01-01')")) {
                    statement.setObject(1, id);
                    statement.executeUpdate();
                }
            }
        }

        flyway(url, null).migrate();

        try (Connection connection = connect(url)) {
            Assertions.assertEquals(1, count(connection, "select count(*) from availability where planning_id = ? and account_id = ?", planning, account));
            Assertions.assertEquals(1, count(connection, "select count(*) from availability where planning_id = ? and account_id = ?", planning, otherAccount));
            Assertions.assertEquals(1, count(connection, "select count(*) from availability where planning_id = ? and account_id = ?", otherPlanning, account));
            Assertions.assertEquals(3, count(connection, "select count(*) from availability"));
            Assertions.assertEquals(3, count(connection, "select count(*) from availability_dates"), "the dates of removed votes go with them");
            Assertions.assertEquals(0, count(connection, "select count(*) from availability_dates d where not exists (select 1 from availability a where a.id = d.availability_id)"));
        }
    }

    @Test
    void two_pseudos_that_differ_only_by_case_stop_the_migration_and_leave_the_database_untouched() throws Exception {
        // the known risk of the case-insensitive uniqueness: it must fail loudly instead of corrupting anything
        String url = newDatabase();
        flyway(url, "1").migrate();
        try (Connection connection = connect(url)) {
            insertAccount(connection, UUID.randomUUID(), "Gandalf");
            insertAccount(connection, UUID.randomUUID(), "gandalf");
        }

        Assertions.assertThrows(FlywayException.class, () -> flyway(url, null).migrate());

        try (Connection connection = connect(url)) {
            Assertions.assertEquals(2, count(connection, "select count(*) from accounts"), "no row lost");
            Assertions.assertEquals(0, count(connection, "select count(*) from pg_indexes where indexname = 'accounts_pseudo_lower_key'"),
                    "the failed script left nothing behind");
        }
        Assertions.assertEquals("1", flyway(url, null).info().current().getVersion().getVersion(),
                "the database stays on the last version that worked");
    }

    @Test
    void message_dates_are_read_as_utc_when_the_column_becomes_a_timestamp_with_time_zone() throws Exception {
        String url = newDatabase();
        flyway(url, "6").migrate();
        UUID sender = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        try (Connection connection = connect(url)) {
            insertAccount(connection, sender, "Sender");
            insertAdventure(connection, adventure, sender, "PUBLIC", null);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'hi', '2026-03-01 10:30:00')")) {
                statement.setObject(1, message);
                statement.setObject(2, adventure);
                statement.setObject(3, sender);
                statement.executeUpdate();
            }
            // a session in another time zone must not change how the old value is read
            try (Statement statement = connection.createStatement()) {
                statement.execute("set time zone 'Asia/Tokyo'");
            }
        }

        flyway(url, null).migrate();

        try (Connection connection = connect(url)) {
            Assertions.assertEquals(1, count(connection,
                    "select count(*) from message where id = ? and send_date = timestamptz '2026-03-01 10:30:00+00'", message));
            Assertions.assertEquals("timestamp with time zone", stringValue(connection,
                    "select data_type from information_schema.columns where table_name = 'message' and column_name = 'send_date'"));
            Assertions.assertEquals(1, count(connection,
                    "select count(*) from pg_indexes where tablename = 'message' and indexname = 'message_adventure_send_date_idx'"));
            Assertions.assertEquals(0, count(connection, "select count(*) from pg_indexes where indexname = 'idx_message_adventure'"),
                    "the old single-column index is covered by the composite one");
        }
    }

    // ---- V12: missing constraints (review v2, point 44) ----

    /** A database on V11 with two accounts and one adventure, to which the tests add what V12 must accept or refuse. */
    private record Fixture(String url, UUID master, UUID player, UUID adventure) { }

    private static Fixture databaseOnVersion11() throws SQLException {
        String url = newDatabase();
        flyway(url, "11").migrate();
        UUID master = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        try (Connection connection = connect(url)) {
            insertAccount(connection, master, "Master");
            insertAccount(connection, player, "Player");
            insertAdventure(connection, adventure, master, "PUBLIC", "CODE000001");
        }
        return new Fixture(url, master, player, adventure);
    }

    private static void assertV12Stops(Fixture fixture, String expectedMessage) {
        FlywayException failure = Assertions.assertThrows(FlywayException.class, () -> flyway(fixture.url(), null).migrate());
        Assertions.assertTrue(failure.getMessage().contains(expectedMessage), failure.getMessage());
        Assertions.assertEquals("11", flyway(fixture.url(), null).info().current().getVersion().getVersion(),
                "the database stays on the last version that worked");
    }

    @Test
    void consistent_data_takes_the_constraints_and_keeps_every_row() throws Exception {
        Fixture fixture = databaseOnVersion11();
        UUID planning = UUID.randomUUID();
        UUID availability = UUID.randomUUID();
        UUID spellBook = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        try (Connection connection = connect(fixture.url())) {
            execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", fixture.adventure(), fixture.player());
            execute(connection, "insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'hi', now())",
                    UUID.randomUUID(), fixture.adventure(), fixture.player());
            execute(connection, "insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, 4)",
                    UUID.randomUUID(), fixture.player(), fixture.master());
            insertPlanning(connection, planning, fixture.adventure());
            execute(connection, "insert into availability (id, planning_id, account_id) values (?, ?, ?)", availability, planning, fixture.player());
            execute(connection, "insert into spellbook (id, account_id, name) values (?, ?, 'Book')", spellBook, fixture.player());
            execute(connection, "insert into spell_book_entity_spell_list (spell_book_entity_id, spell_list) values (?, 'Fireball')", spellBook);
            execute(connection, "insert into master_claims (id, adventure_id, candidate_id, created_at, expires_at, status) "
                    + "values (?, ?, ?, now(), now(), 'OPEN')", claim, fixture.adventure(), fixture.player());
            execute(connection, "insert into master_claim_votes (claim_id, voter_id, approve) values (?, ?, true)", claim, fixture.master());
        }

        flyway(fixture.url(), null).migrate();

        try (Connection connection = connect(fixture.url())) {
            Assertions.assertEquals(1, count(connection, "select count(*) from adventure_players"));
            Assertions.assertEquals(1, count(connection, "select count(*) from message"));
            Assertions.assertEquals(1, count(connection, "select count(*) from rating"));
            Assertions.assertEquals(1, count(connection, "select count(*) from availability"));
            Assertions.assertEquals(1, count(connection, "select count(*) from master_claim_votes"));
            Assertions.assertEquals("Fireball", stringValue(connection, "select spell from spellbook_spells where spellbook_id = '" + spellBook + "'"),
                    "the spell list table has its new name, and its rows");
            Assertions.assertEquals(0, count(connection, "select count(*) from information_schema.tables where table_name = 'spell_book_entity_spell_list'"));
        }
    }

    @Test
    void a_reference_to_a_row_that_does_not_exist_stops_the_migration() throws Exception {
        UUID ghost = UUID.randomUUID();
        // one case per kind of target: account, adventure, planning
        Fixture account = databaseOnVersion11();
        try (Connection connection = connect(account.url())) {
            execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", account.adventure(), ghost);
        }
        assertV12Stops(account, "adventure_players.player_id " + ghost);

        Fixture adventure = databaseOnVersion11();
        try (Connection connection = connect(adventure.url())) {
            execute(connection, "insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'hi', now())",
                    UUID.randomUUID(), ghost, adventure.player());
        }
        assertV12Stops(adventure, "message.adventure_id " + ghost);

        Fixture planning = databaseOnVersion11();
        try (Connection connection = connect(planning.url())) {
            execute(connection, "insert into availability (id, planning_id, account_id) values (?, ?, ?)", UUID.randomUUID(), ghost, planning.player());
        }
        assertV12Stops(planning, "availability.planning_id " + ghost);
    }

    @Test
    void a_player_listed_twice_stops_the_migration() throws Exception {
        Fixture fixture = databaseOnVersion11();
        try (Connection connection = connect(fixture.url())) {
            for (int i = 0; i < 2; i++) {
                execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", fixture.adventure(), fixture.player());
            }
        }

        assertV12Stops(fixture, "lists player " + fixture.player() + " twice");
    }

    @Test
    void two_ratings_of_the_same_account_by_the_same_account_stop_the_migration() throws Exception {
        Fixture fixture = databaseOnVersion11();
        try (Connection connection = connect(fixture.url())) {
            for (int rate : new int[]{3, 5}) {
                execute(connection, "insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, ?)",
                        UUID.randomUUID(), fixture.player(), fixture.master(), rate);
            }
        }

        assertV12Stops(fixture, "more than once");
    }

    @Test
    void an_invitation_code_shared_by_two_adventures_stops_the_migration() throws Exception {
        Fixture fixture = databaseOnVersion11();
        try (Connection connection = connect(fixture.url())) {
            insertAdventure(connection, UUID.randomUUID(), fixture.master(), "PUBLIC", "CODE000001");
        }

        assertV12Stops(fixture, "CODE000001");
    }

    @Test
    void an_adventure_without_visibility_becomes_private_and_the_column_refuses_null_afterwards() throws Exception {
        Fixture fixture = databaseOnVersion11();
        UUID unknown = UUID.randomUUID();
        try (Connection connection = connect(fixture.url())) {
            insertAdventure(connection, unknown, fixture.master(), null, null);
        }

        flyway(fixture.url(), null).migrate();

        try (Connection connection = connect(fixture.url())) {
            Assertions.assertEquals("PRIVATE", stringValue(connection, "select visibility from adventure where id = '" + unknown + "'"));
            Assertions.assertEquals("PUBLIC", stringValue(connection, "select visibility from adventure where id = '" + fixture.adventure() + "'"),
                    "a known visibility is left alone");
            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), fixture.master(), null, null));
        }
    }

    // ---- V13: rating creation date (review v2, point 47) ----

    @Test
    void existing_ratings_get_a_creation_date_and_the_column_refuses_null_afterwards() throws Exception {
        Fixture fixture = databaseOnVersion11();
        flyway(fixture.url(), "12").migrate();
        UUID rating = UUID.randomUUID();
        try (Connection connection = connect(fixture.url())) {
            execute(connection, "insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, 4)",
                    rating, fixture.player(), fixture.master());
        }

        flyway(fixture.url(), null).migrate();

        try (Connection connection = connect(fixture.url())) {
            Assertions.assertEquals(1, count(connection, "select count(*) from rating where id = ? and created_at is not null", rating));
            Assertions.assertEquals("timestamp with time zone", stringValue(connection,
                    "select data_type from information_schema.columns where table_name = 'rating' and column_name = 'created_at'"));
            Assertions.assertThrows(SQLException.class, () -> execute(connection,
                    "insert into rating (id, emitter_id, target_id, rate, created_at) values (?, ?, ?, 3, null)",
                    UUID.randomUUID(), fixture.master(), fixture.player()));
        }
    }

    @Test
    void once_up_to_date_the_database_itself_refuses_what_the_code_used_to_check() throws Exception {
        String url = newDatabase();
        flyway(url, null).migrate();
        UUID master = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        try (Connection connection = connect(url)) {
            insertAccount(connection, master, "Master");
            insertAccount(connection, player, "Player");
            insertAdventure(connection, adventure, master, "PUBLIC", "CODE000001");
            execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", adventure, player);
            execute(connection, "insert into rating (id, emitter_id, target_id, rate, created_at) values (?, ?, ?, 4, now())", UUID.randomUUID(), player, master);

            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), ghost, "PUBLIC", null), "creator");
            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), master, "PUBLIC", "CODE000001"), "code");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", adventure, player), "twice");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, null)", adventure), "null player");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, 2)", UUID.randomUUID(), player, master), "rating");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'x', now())",
                            UUID.randomUUID(), ghost, player), "message adventure");
            Assertions.assertThrows(SQLException.class, () -> insertPlanning(connection, UUID.randomUUID(), ghost), "planning");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into master_claims (id, adventure_id, candidate_id, created_at, expires_at, status) "
                            + "values (?, ?, ?, now(), now(), 'OPEN')", UUID.randomUUID(), adventure, ghost), "claim candidate");
        }
    }


    // ---- V14: case-insensitive e-mail addresses (review v2, point 32) ----

    private static void insertAccountWithEmail(Connection connection, String pseudo, String email, String pendingEmail) throws SQLException {
        execute(connection, "insert into accounts (id, email, password, pseudo, activated, pending_email) values (?, ?, 'hash', ?, true, ?)",
                UUID.randomUUID(), email, pseudo, pendingEmail);
    }

    @Test
    void existing_addresses_are_lower_cased_and_the_database_refuses_a_case_variant_afterwards() throws Exception {
        String url = newDatabase();
        flyway(url, "13").migrate();
        try (Connection connection = connect(url)) {
            insertAccountWithEmail(connection, "Alice", "Alice@Mail.COM", "New.Alice@Mail.COM");
            insertAccountWithEmail(connection, "Bob", "bob@mail.com", null);
        }

        flyway(url, null).migrate();

        try (Connection connection = connect(url)) {
            Assertions.assertEquals(2, count(connection, "select count(*) from accounts"), "no row lost");
            Assertions.assertEquals("alice@mail.com", stringValue(connection, "select email from accounts where pseudo = 'Alice'"));
            Assertions.assertEquals("new.alice@mail.com", stringValue(connection, "select pending_email from accounts where pseudo = 'Alice'"));
            Assertions.assertEquals(1, count(connection, "select count(*) from accounts where pseudo = 'Bob' and pending_email is null"));
            Assertions.assertThrows(SQLException.class, () -> insertAccountWithEmail(connection, "Carol", "ALICE@mail.com", null));
        }
    }

    @Test
    void two_addresses_that_differ_only_by_case_stop_the_migration_and_leave_the_database_untouched() throws Exception {
        String url = newDatabase();
        flyway(url, "13").migrate();
        try (Connection connection = connect(url)) {
            insertAccountWithEmail(connection, "Alice", "Bob@Mail.com", null);
            insertAccountWithEmail(connection, "Bobby", "bob@mail.com", null);
        }

        FlywayException failure = Assertions.assertThrows(FlywayException.class, () -> flyway(url, null).migrate());

        Assertions.assertTrue(failure.getMessage().contains("V14 stopped") && failure.getMessage().contains("bob@mail.com"), failure.getMessage());
        try (Connection connection = connect(url)) {
            Assertions.assertEquals("Bob@Mail.com", stringValue(connection, "select email from accounts where pseudo = 'Alice'"),
                    "nothing was lower-cased");
            Assertions.assertEquals(0, count(connection, "select count(*) from pg_indexes where indexname = 'accounts_email_lower_key'"));
        }
        Assertions.assertEquals("13", flyway(url, null).info().current().getVersion().getVersion());
    }

    private static String stringValue(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
