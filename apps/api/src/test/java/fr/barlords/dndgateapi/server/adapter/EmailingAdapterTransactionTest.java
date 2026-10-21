package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferEvent;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import fr.barlords.dndgateapi.server.mail.EmailContentBuilder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The e-mails about a change wait for the commit of the transaction in which the change is made. */
class EmailingAdapterTransactionTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private final AtomicInteger handedToTheOutbox = new AtomicInteger();

    /** Runs the task at once and counts it. */
    private final Executor outbox = task -> {
        handedToTheOutbox.incrementAndGet();
        task.run();
    };

    private final EmailingAdapter adapter = new EmailingAdapter(
            mailSender, new EmailContentBuilder("https://dndgate.example.com"), outbox, "noreply@dndgate.example.com");

    private final Account receiver = Account.builder().pseudo("Frodo").email("frodo@middle.earth").build();

    private final MasterTransferMessage message =
            new MasterTransferMessage(MasterTransferEvent.MASTER_CHANGED, UUID.randomUUID(), "The lost mine", "Sam");

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outside_a_transaction_the_mail_leaves_at_once() {
        adapter.sendMasterTransferEmail(receiver, message);

        Assertions.assertEquals(1, handedToTheOutbox.get());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void inside_a_transaction_the_mail_waits_for_the_commit() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.sendMasterTransferEmail(receiver, message);

        Assertions.assertEquals(0, handedToTheOutbox.get(), "nothing leaves before the commit");
        verify(mailSender, never()).send(any(MimeMessage.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        Assertions.assertEquals(1, handedToTheOutbox.get());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void a_transaction_that_is_rolled_back_sends_nothing() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.sendMasterTransferEmail(receiver, message);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        Assertions.assertEquals(0, handedToTheOutbox.get());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
