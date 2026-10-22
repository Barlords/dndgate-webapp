package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The password typed to confirm a sensitive change is checked before the transaction of the change opens.
 * Checking it inside held one pooled connection during the hashing, and recording a failure needs a second one
 * (its own transaction, so that it survives the refusal): a few simultaneous wrong passwords emptied the pool,
 * every request waited for a connection until the timeout (500), and no failure was recorded.
 * A pool smaller than the number of callers makes that visible at once.
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=4000"})
class PasswordCheckConnectionPoolIntegrationTest extends ApiIntegrationTestSupport {

    private static final int CALLERS = 6;

    private List<ResponseEntity<String>> inParallel(List<TestAccount> accounts, Function<TestAccount, ResponseEntity<String>> request)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(accounts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> results = new ArrayList<>();
            for (TestAccount account : accounts) {
                Callable<ResponseEntity<String>> call = () -> {
                    start.await();
                    return request.apply(account);
                };
                results.add(pool.submit(call));
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

    private List<TestAccount> accounts() {
        List<TestAccount> accounts = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            accounts.add(registerAndLogin());
        }
        return accounts;
    }

    private void assertRefusedAndCounted(List<TestAccount> accounts, List<ResponseEntity<String>> responses) {
        for (ResponseEntity<String> response : responses) {
            Assertions.assertEquals(403, response.getStatusCode().value(), response.getBody());
        }
        for (TestAccount account : accounts) {
            Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from login_failures where account_id = ?", Integer.class,
                    account.id()), "the failure is recorded");
        }
    }

    @Test
    void simultaneous_wrong_passwords_on_an_account_change_are_refused_and_counted() throws Exception {
        List<TestAccount> accounts = accounts();

        List<ResponseEntity<String>> responses = inParallel(accounts, account -> call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", "wrong-password", "pseudo", "renamed" + account.pseudo())));

        assertRefusedAndCounted(accounts, responses);
    }

    @Test
    void simultaneous_wrong_passwords_on_an_account_deletion_are_refused_and_counted() throws Exception {
        List<TestAccount> accounts = accounts();

        List<ResponseEntity<String>> responses = inParallel(accounts, account -> call(HttpMethod.DELETE, "/accounts", account.token(),
                Map.of("confirmation", "wrong-password")));

        assertRefusedAndCounted(accounts, responses);
    }
}
