package fr.barlords.dndgateapi.integration;

import org.flywaydb.core.Flyway;
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
 * The schema is only changed by Flyway. These tests migrate real PostgreSQL databases the way a deployment does, and
 * check that the database itself refuses what the code relies on it to refuse.
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

    private static Flyway flyway(String url) {
        return Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    /** A database with the whole schema, ready for the tests to insert rows. */
    private static String migratedDatabase() throws SQLException {
        String url = newDatabase();
        flyway(url).migrate();
        return url;
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

    private static String stringValue(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private static void insertAccount(Connection connection, UUID id, String pseudo) throws SQLException {
        execute(connection, "insert into accounts (id, email, password, pseudo, activated) values (?, ?, 'hash', ?, true)",
                id, pseudo.toLowerCase() + "-" + id + "@test.fr", pseudo);
    }

    private static void insertAccountWithEmail(Connection connection, String pseudo, String email) throws SQLException {
        execute(connection, "insert into accounts (id, email, password, pseudo, activated) values (?, ?, 'hash', ?, true)",
                UUID.randomUUID(), email, pseudo);
    }

    private static void insertAdventure(Connection connection, UUID id, UUID creator, String visibility, String invitationCode) throws SQLException {
        execute(connection, "insert into adventure (id, creator_id, name, description, max_player, status, visibility, invitation_code) "
                + "values (?, ?, 'Quest', 'A quest', 4, 'NOT_STARTED', ?, ?)", id, creator, visibility, invitationCode);
    }

    private static void insertPlanning(Connection connection, UUID id, UUID adventure) throws SQLException {
        execute(connection, "insert into planning (id, adventure_id) values (?, ?)", id, adventure);
    }

    // ---- from nothing ----

    @Test
    void an_empty_database_gets_every_migration_in_order_without_error() throws Exception {
        String url = newDatabase();
        Flyway flyway = flyway(url);

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
        String url = migratedDatabase();

        Assertions.assertEquals(0, flyway(url).migrate().migrationsExecuted);
    }

    // ---- what the database refuses by itself ----

    @Test
    void a_pseudo_is_unique_whatever_its_case() throws Exception {
        try (Connection connection = connect(migratedDatabase())) {
            insertAccount(connection, UUID.randomUUID(), "Gandalf");

            Assertions.assertThrows(SQLException.class, () -> insertAccount(connection, UUID.randomUUID(), "gandalf"));
        }
    }

    @Test
    void an_email_address_is_unique_whatever_its_case() throws Exception {
        try (Connection connection = connect(migratedDatabase())) {
            insertAccountWithEmail(connection, "Alice", "alice@mail.com");

            Assertions.assertThrows(SQLException.class, () -> insertAccountWithEmail(connection, "Carol", "ALICE@mail.com"));
        }
    }

    @Test
    void an_account_votes_once_per_planning() throws Exception {
        UUID master = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID planning = UUID.randomUUID();
        try (Connection connection = connect(migratedDatabase())) {
            insertAccount(connection, master, "Master");
            insertAdventure(connection, adventure, master, "PUBLIC", null);
            insertPlanning(connection, planning, adventure);
            execute(connection, "insert into availability (id, planning_id, account_id) values (?, ?, ?)", UUID.randomUUID(), planning, master);

            Assertions.assertThrows(SQLException.class, () -> execute(connection,
                    "insert into availability (id, planning_id, account_id) values (?, ?, ?)", UUID.randomUUID(), planning, master));
        }
    }

    @Test
    void the_database_refuses_what_the_code_relies_on_it_to_refuse() throws Exception {
        UUID master = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        try (Connection connection = connect(migratedDatabase())) {
            insertAccount(connection, master, "Master");
            insertAccount(connection, player, "Player");
            insertAdventure(connection, adventure, master, "PUBLIC", "CODE000001");
            execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", adventure, player);
            execute(connection, "insert into rating (id, emitter_id, target_id, rate, created_at) values (?, ?, ?, 4, now())", UUID.randomUUID(), player, master);

            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), ghost, "PUBLIC", null), "creator");
            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), master, null, null), "visibility");
            Assertions.assertThrows(SQLException.class, () -> insertAdventure(connection, UUID.randomUUID(), master, "PUBLIC", "CODE000001"), "code");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, ?)", adventure, player), "twice");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into adventure_players (adventure_id, player_id) values (?, null)", adventure), "null player");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into rating (id, emitter_id, target_id, rate, created_at) values (?, ?, ?, 2, now())",
                            UUID.randomUUID(), player, master), "rated twice");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, 2)",
                            UUID.randomUUID(), master, player), "rating without date");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'x', now())",
                            UUID.randomUUID(), ghost, player), "message adventure");
            Assertions.assertThrows(SQLException.class, () -> insertPlanning(connection, UUID.randomUUID(), ghost), "planning");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into master_claims (id, adventure_id, candidate_id, created_at, expires_at, status) "
                            + "values (?, ?, ?, now(), now(), 'OPEN')", UUID.randomUUID(), adventure, ghost), "claim candidate");
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into spellbook (id, account_id, name) values (?, ?, 'Book')", UUID.randomUUID(), master),
                    "spell book without ruleset");
        }
    }

    @Test
    void one_open_master_claim_at_a_time_per_adventure() throws Exception {
        UUID master = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        try (Connection connection = connect(migratedDatabase())) {
            insertAccount(connection, master, "Master");
            insertAccount(connection, player, "Player");
            insertAdventure(connection, adventure, master, "PUBLIC", null);
            String claim = "insert into master_claims (id, adventure_id, candidate_id, created_at, expires_at, status) values (?, ?, ?, now(), now(), ?)";
            execute(connection, claim, UUID.randomUUID(), adventure, player, "OPEN");
            execute(connection, claim, UUID.randomUUID(), adventure, player, "REJECTED");

            Assertions.assertThrows(SQLException.class, () -> execute(connection, claim, UUID.randomUUID(), adventure, master, "OPEN"));
        }
    }

    @Test
    void dates_that_are_instants_keep_their_time_zone() throws Exception {
        try (Connection connection = connect(migratedDatabase())) {
            String type = "select data_type from information_schema.columns where table_name = '%s' and column_name = '%s'";
            Assertions.assertEquals("timestamp with time zone", stringValue(connection, type.formatted("message", "send_date")));
            Assertions.assertEquals("timestamp with time zone", stringValue(connection, type.formatted("rating", "created_at")));
        }
    }

    @Test
    void signup_notices_and_pending_signups_keep_one_row_per_address() throws Exception {
        try (Connection connection = connect(migratedDatabase())) {
            execute(connection, "insert into signup_notices (email_hash, sent_at) values (?, now())", "a".repeat(64));
            Assertions.assertThrows(SQLException.class,
                    () -> execute(connection, "insert into signup_notices (email_hash, sent_at) values (?, now())", "a".repeat(64)));

            String signup = "insert into pending_signups (id, email, pseudo, password_hash, code_hash, created_at, expires_at) "
                    + "values (?, 'alice@mail.com', ?, 'hash', 'code', now(), now())";
            execute(connection, signup, UUID.randomUUID(), "Alice");
            Assertions.assertThrows(SQLException.class, () -> execute(connection, signup, UUID.randomUUID(), "Alicia"));

            Assertions.assertEquals(1, count(connection, "select count(*) from pg_indexes where indexname = 'idx_signup_notices_sent_at'"));
            Assertions.assertEquals(1, count(connection, "select count(*) from pg_indexes where indexname = 'idx_pending_signups_expires_at'"));
        }
    }
}
