package fr.barlords.dndgateapi.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.barlords.dndgateapi.domain.port.in.account.AccountAnonymizerApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureDeleterApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The audit line says that something was deleted: it is written when the database has accepted the deletion,
 * never before. A deletion whose transaction is rolled back, or not yet committed, leaves no line.
 */
class DeletionAuditCommitIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    AdventureDeleterApi adventureDeleter;

    @Autowired
    AccountAnonymizerApi accountAnonymizer;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final ListAppender<ILoggingEvent> audit = new ListAppender<>();

    private Logger auditLogger;

    @BeforeEach
    void listen() {
        auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        audit.start();
        auditLogger.addAppender(audit);
    }

    @AfterEach
    void stopListening() {
        auditLogger.detachAppender(audit);
        audit.stop();
    }

    private List<ILoggingEvent> linesAbout(UUID subjectId) {
        return List.copyOf(audit.list).stream()
                .filter(event -> event.getKeyValuePairs().stream().anyMatch(pair -> subjectId.toString().equals(String.valueOf(pair.value))))
                .toList();
    }

    private int adventures(UUID id) {
        return jdbc.queryForObject("select count(*) from adventure where id = ?", Integer.class, id);
    }

    // ---- adventure ----

    @Test
    void an_adventure_deletion_that_is_rolled_back_leaves_no_line() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        Assertions.assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adventureDeleter.deleteById(master.id(), adventureId, "Quest");
            throw new IllegalStateException("something fails before the commit");
        }));

        Assertions.assertEquals(1, adventures(adventureId), "the deletion was rolled back");
        Assertions.assertTrue(linesAbout(adventureId).isEmpty(), String.valueOf(linesAbout(adventureId)));
    }

    @Test
    void an_adventure_deletion_is_traced_once_the_transaction_is_committed_and_not_before() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adventureDeleter.deleteById(master.id(), adventureId, "Quest");
            Assertions.assertTrue(linesAbout(adventureId).isEmpty(), "nothing is committed yet");
        });

        Assertions.assertEquals(0, adventures(adventureId));
        Assertions.assertEquals(1, linesAbout(adventureId).size());
    }

    // ---- account ----

    @Test
    void an_account_deletion_that_is_rolled_back_leaves_no_line() {
        TestAccount account = registerAndLogin();

        Assertions.assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountAnonymizer.anonymize(account.id(), account.password(), new fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress("203.0.113.7"));
            throw new IllegalStateException("something fails before the commit");
        }));

        Assertions.assertNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", Object.class, account.id()),
                "the anonymization was rolled back");
        Assertions.assertTrue(linesAbout(account.id()).isEmpty(), String.valueOf(linesAbout(account.id())));
    }

    @Test
    void an_account_deletion_is_traced_once_the_transaction_is_committed_and_not_before() {
        TestAccount account = registerAndLogin();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountAnonymizer.anonymize(account.id(), account.password(), new fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress("203.0.113.7"));
            Assertions.assertTrue(linesAbout(account.id()).isEmpty(), "nothing is committed yet");
        });

        Assertions.assertEquals(1, linesAbout(account.id()).size());
    }
}
