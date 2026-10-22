package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.server.mail.EmailContentBuilder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailingAdapterTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());

    private final EmailingAdapter adapter = new EmailingAdapter(
            mailSender, new EmailContentBuilder("https://dndgate.example.com"), executor, "noreply@dndgate.example.com");

    private final Account account = Account.builder()
            .pseudo("Gandalf").email("gandalf@middle.earth").verificationCode("abc123").build();

    EmailingAdapterTest() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void the_caller_does_not_wait_for_the_mail_server() throws Exception {
        CountDownLatch smtpReached = new CountDownLatch(1);
        CountDownLatch smtpMayAnswer = new CountDownLatch(1);
        doAnswer(call -> {
            smtpReached.countDown();
            smtpMayAnswer.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3), () -> adapter.sendAccountValidationEmail(account),
                "the call must return while the mail server has not answered yet");

        Assertions.assertTrue(smtpReached.await(5, TimeUnit.SECONDS), "the mail must still be handed to the server");
        smtpMayAnswer.countDown();
    }

    @Test
    void a_mail_server_failure_does_not_reach_the_caller() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(call -> {
            attempted.countDown();
            throw new MailSendException("connection refused");
        }).when(mailSender).send(any(MimeMessage.class));

        Assertions.assertDoesNotThrow(() -> adapter.sendAccountValidationEmail(account));

        Assertions.assertTrue(attempted.await(5, TimeUnit.SECONDS));
    }

    @Test
    void a_full_outbox_drops_the_mail_instead_of_blocking_or_failing_the_caller() throws Exception {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sent = new AtomicInteger();
        doAnswer(call -> {
            sent.incrementAndGet();
            busy.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        adapter.sendAccountValidationEmail(account);
        Assertions.assertTrue(busy.await(5, TimeUnit.SECONDS));

        // the only worker is busy and the queue has no room
        Assertions.assertDoesNotThrow(() -> adapter.sendAccountValidationEmail(account));
        Assertions.assertDoesNotThrow(() -> adapter.sendInvitationCodeEmail(account, account, "code"));

        release.countDown();
        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        Assertions.assertEquals(1, sent.get());
    }

    @Test
    void the_signup_confirmation_is_handed_to_the_mail_server_for_the_address_of_the_signup() throws Exception {
        CountDownLatch handed = new CountDownLatch(1);
        doAnswer(call -> {
            MimeMessage message = call.getArgument(0);
            Assertions.assertEquals("gandalf@middle.earth", message.getAllRecipients()[0].toString());
            handed.countDown();
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        adapter.sendSignupConfirmationEmail(
                PendingSignup.builder().pseudo("Gandalf").email("gandalf@middle.earth").build(), "abc123");

        Assertions.assertTrue(handed.await(5, TimeUnit.SECONDS));
    }
}
