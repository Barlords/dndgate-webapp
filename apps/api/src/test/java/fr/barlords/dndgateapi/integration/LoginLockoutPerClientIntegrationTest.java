package fr.barlords.dndgateapi.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import fr.barlords.dndgateapi.domain.functional.service.account.AccountPasswordVerifierService;
import fr.barlords.dndgateapi.domain.port.in.account.LoginFailureCleanerApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Review v2, point 27: the lockout is per (account, client address), so that a stranger who knows a pseudo
 * cannot keep its owner out. The tests play several clients through X-Forwarded-For, which the container
 * honours because the call comes from a trusted proxy (the loopback address), like behind the reverse proxy
 * of production. Own application context for that reason.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                "management.server.port=0",
                "server.forward-headers-strategy=native",
                "security.login.distinct-clients-warning-threshold=3",
                "security.rate-limit.login.max=1000000",
                "security.rate-limit.refresh.max=1000000",
                "security.rate-limit.register.max=1000000",
                "security.rate-limit.verify.max=1000000",
                "security.rate-limit.resend.max=1000000"})
@ActiveProfiles("dev")
class LoginLockoutPerClientIntegrationTest extends ApiIntegrationTestSupport {

    private static final String ADDRESS_A = "203.0.113.10";

    private static final String ADDRESS_B = "198.51.100.20";

    @Autowired
    private LoginFailureCleanerApi loginFailureCleaner;

    private ResponseEntity<String> from(String address, HttpMethod method, String path, String token, Object body) {
        var spec = client().method(method).uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.add("X-Forwarded-For", address));
        if (token != null) {
            spec.headers(headers -> headers.setBearerAuth(token));
        }
        if (body != null) {
            spec.body(body);
        }
        return spec.retrieve().toEntity(String.class);
    }

    private int login(String address, TestAccount account, String password) {
        return from(address, HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", password)).getStatusCode().value();
    }

    private int delete(String address, TestAccount account, String confirmation) {
        return from(address, HttpMethod.DELETE, "/accounts", account.token(), Map.of("confirmation", confirmation))
                .getStatusCode().value();
    }

    private ResponseEntity<String> patchPseudo(String address, TestAccount account, String currentPassword, String pseudo) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("pseudo", pseudo);
        return from(address, HttpMethod.PATCH, "/accounts", account.token(), body);
    }

    @Test
    void five_failed_logins_lock_the_account_for_that_address_only() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(401, login(ADDRESS_A, account, "wrong-" + i));
        }

        ResponseEntity<String> locked = from(ADDRESS_A, HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password()));
        Assertions.assertEquals(429, locked.getStatusCode().value(), "the right password is refused for the address that failed");
        long retryAfter = Long.parseLong(locked.getHeaders().getFirst("Retry-After"));
        Assertions.assertTrue(retryAfter > 800 && retryAfter <= 900, "locked for about 15 minutes but was " + retryAfter + "s");

        Assertions.assertEquals(200, login(ADDRESS_B, account, account.password()), "another address is not concerned");
        Assertions.assertEquals(429, login(ADDRESS_A, account, account.password()), "and the first one stays locked");
    }

    @Test
    void the_failures_of_two_addresses_are_not_added_together() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(401, login(ADDRESS_A, account, "wrong-" + i));
            Assertions.assertEquals(401, login(ADDRESS_B, account, "wrong-" + i));
        }

        Assertions.assertEquals(200, login(ADDRESS_A, account, account.password()), "3 failures from A are below the limit");
        Assertions.assertEquals(200, login(ADDRESS_B, account, account.password()), "3 failures from B are below the limit");
    }

    @Test
    void a_locked_address_does_not_lock_the_deletion_nor_the_change_of_the_owner_elsewhere() {
        TestAccount account = registerAndLogin();
        for (int i = 0; i < 5; i++) {
            login(ADDRESS_A, account, "wrong-" + i);
        }

        Assertions.assertEquals(200, patchPseudo(ADDRESS_B, account, account.password(), "b" + account.pseudo()).getStatusCode().value());
        Assertions.assertEquals(204, delete(ADDRESS_B, account, account.password()));
    }

    @Test
    void wrong_passwords_given_to_delete_the_account_lock_that_address_for_every_entry_point() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(403, delete(ADDRESS_A, account, "guess-" + i));
        }

        Assertions.assertEquals(429, delete(ADDRESS_A, account, account.password()));
        Assertions.assertEquals(429, login(ADDRESS_A, account, account.password()));
        Assertions.assertEquals(429, patchPseudo(ADDRESS_A, account, account.password(), "x" + account.pseudo()).getStatusCode().value());

        Assertions.assertEquals(200, login(ADDRESS_B, account, account.password()));
        Assertions.assertEquals(204, delete(ADDRESS_B, account, account.password()));
    }

    @Test
    void wrong_current_passwords_given_to_change_the_account_lock_that_address_for_every_entry_point() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(403, patchPseudo(ADDRESS_A, account, "guess-" + i, "x" + account.pseudo()).getStatusCode().value());
        }

        Assertions.assertEquals(429, patchPseudo(ADDRESS_A, account, account.password(), "x" + account.pseudo()).getStatusCode().value());
        Assertions.assertEquals(429, login(ADDRESS_A, account, account.password()));
        Assertions.assertEquals(429, delete(ADDRESS_A, account, account.password()));

        Assertions.assertEquals(200, patchPseudo(ADDRESS_B, account, account.password(), "b" + account.pseudo()).getStatusCode().value());
    }

    @Test
    void only_a_hash_of_the_address_is_stored() {
        TestAccount account = registerAndLogin();
        login(ADDRESS_A, account, "wrong");

        String stored = jdbc.queryForObject("select client_hash from login_failures where account_id = ?", String.class, account.id());

        Assertions.assertEquals(sha256Hex(ADDRESS_A), stored, "the hash of the address under test, not the one of the proxy");
        Assertions.assertFalse(stored.contains(ADDRESS_A));
    }

    @Test
    void an_expired_lock_restarts_the_count_from_one() {
        TestAccount account = registerAndLogin();
        jdbc.update("insert into login_failures (id, account_id, client_hash, failed_attempts, locked_until, last_failed_at) "
                + "values (?, ?, ?, 5, now() - interval '1 minute', now() - interval '16 minutes')",
                UUID.randomUUID(), account.id(), sha256Hex(ADDRESS_A));

        Assertions.assertEquals(401, login(ADDRESS_A, account, "wrong"), "the lock is over: the password is examined, not refused with 429");
        Assertions.assertEquals(1, jdbc.queryForObject("select failed_attempts from login_failures where account_id = ?", Integer.class, account.id()));
        Assertions.assertNull(jdbc.queryForObject("select locked_until from login_failures where account_id = ?", Object.class, account.id()));
        Assertions.assertEquals(200, login(ADDRESS_A, account, account.password()));
    }

    @Test
    void ipv6_addresses_of_the_same_64_share_one_counter_and_other_prefixes_are_free() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(401, login("2001:db8:1:2::" + (i + 1), account, "wrong-" + i));
        }

        Assertions.assertEquals(429, login("2001:db8:1:2:ffff:ffff:ffff:ffff", account, account.password()), "rotating inside the /64 does not help");
        Assertions.assertEquals(200, login("2001:db8:1:3::1", account, account.password()), "another /64 is not concerned");
    }

    @Test
    void deleting_the_account_forgets_its_login_failures() {
        TestAccount account = registerAndLogin();
        login(ADDRESS_A, account, "wrong");
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from login_failures where account_id = ?", Integer.class, account.id()));

        Assertions.assertEquals(204, delete(ADDRESS_B, account, account.password()));

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from login_failures where account_id = ?", Integer.class, account.id()));
    }

    private static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void a_good_login_forgets_the_failures_of_that_address() {
        TestAccount account = registerAndLogin();
        for (int i = 0; i < 4; i++) {
            login(ADDRESS_A, account, "wrong-" + i);
        }
        Assertions.assertEquals(200, login(ADDRESS_A, account, account.password()));

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from login_failures where account_id = ?", Integer.class, account.id()));
        for (int i = 0; i < 4; i++) {
            Assertions.assertEquals(401, login(ADDRESS_A, account, "again-" + i));
        }
        Assertions.assertEquals(200, login(ADDRESS_A, account, account.password()), "the counter restarted from zero");
    }

    @Test
    void many_addresses_failing_against_one_account_log_a_warning_and_block_nobody() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger(AccountPasswordVerifierService.class);
        logger.addAppender(logs);
        try {
            TestAccount account = registerAndLogin();

            login("192.0.2.1", account, "wrong");
            login("192.0.2.2", account, "wrong");
            Assertions.assertTrue(logs.list.stream().noneMatch(e -> e.getLevel() == Level.WARN), "two addresses are below the threshold");
            login("192.0.2.3", account, "wrong");

            Assertions.assertEquals(1, logs.list.stream().filter(e -> e.getLevel() == Level.WARN).count());
            Assertions.assertTrue(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)
                    .allMatch(e -> e.getFormattedMessage().contains(account.id().toString())
                            && !e.getFormattedMessage().contains("192.0.2.")));
            login("192.0.2.4", account, "wrong");
            Assertions.assertEquals(1, logs.list.stream().filter(e -> e.getLevel() == Level.WARN).count(), "once per crossing, not on every failure");
            Assertions.assertEquals(200, login("192.0.2.5", account, account.password()), "nothing is blocked");
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void the_nightly_purge_forgets_the_old_failures_and_keeps_the_recent_ones() {
        TestAccount account = registerAndLogin();
        login(ADDRESS_A, account, "wrong");
        jdbc.update("insert into login_failures (id, account_id, client_hash, failed_attempts, locked_until, last_failed_at) "
                + "values (?, ?, 'old', 5, now() - interval '1 hour', now() - interval '2 hours')", UUID.randomUUID(), account.id());

        loginFailureCleaner.purgeExpired();

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from login_failures where client_hash = 'old'", Integer.class));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from login_failures where account_id = ?", Integer.class, account.id()));
    }

}
