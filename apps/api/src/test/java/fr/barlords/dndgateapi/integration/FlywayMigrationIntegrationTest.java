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
}
