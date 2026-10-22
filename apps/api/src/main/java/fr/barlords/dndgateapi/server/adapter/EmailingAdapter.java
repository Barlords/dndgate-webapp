package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.server.mail.AfterCommit;
import fr.barlords.dndgateapi.server.mail.EmailContent;
import fr.barlords.dndgateapi.server.mail.EmailContentBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends the e-mails in the background: the content is written in the caller's thread, the exchange with the mail
 * server happens in the outbox. A failure is logged, never raised to the caller (nobody is waiting for the answer).
 */
@Slf4j
@Service
public class EmailingAdapter implements EmailingSpi {

    private static final String SENDER_NAME = "DnD Gate";

    private final JavaMailSender emailSender;

    private final EmailContentBuilder contentBuilder;

    private final Executor outbox;

    private final String emailSenderUsername;

    public EmailingAdapter(JavaMailSender emailSender,
                           EmailContentBuilder contentBuilder,
                           @Qualifier("mailExecutor") Executor outbox,
                           @Value("${spring.mail.username}") String emailSenderUsername) {
        this.emailSender = emailSender;
        this.contentBuilder = contentBuilder;
        this.outbox = outbox;
        this.emailSenderUsername = emailSenderUsername;
    }

    @Override
    public void sendAccountValidationEmail(Account account) {
        enqueue(account.getEmail(), contentBuilder.accountValidation(account));
    }

    @Override
    public void sendSignupAttemptEmail(Account owner) {
        enqueue(owner.getEmail(), contentBuilder.signupAttempt(owner));
    }

    @Override
    public void sendEmailChangeEmail(Account account) {
        enqueue(account.getPendingEmail(), contentBuilder.emailChange(account));
    }

    @Override
    public void sendInvitationCodeEmail(Account sender, Account receiver, String code) {
        enqueue(receiver.getEmail(), contentBuilder.invitation(sender, receiver, code));
    }

    @Override
    public void sendMasterTransferEmail(Account receiver, MasterTransferMessage message) {
        enqueue(receiver.getEmail(), contentBuilder.masterTransfer(receiver, message));
    }

    /**
     * Inside a transaction the mail waits for the commit: a change that is rolled back, or replayed after a conflict,
     * must not send anything (or send twice).
     */
    private void enqueue(String to, EmailContent content) {
        AfterCommit.run(() -> enqueueNow(to, content));
    }

    private void enqueueNow(String to, EmailContent content) {
        try {
            outbox.execute(() -> sendNow(to, content));
        } catch (RejectedExecutionException e) {
            log.warn("The e-mail outbox is full: the e-mail \"{}\" was dropped", content.subject());
        }
    }

    private void sendNow(String to, EmailContent content) {
        try {
            send(to, content);
        } catch (EmailNotSendException e) {
            log.warn("The e-mail \"{}\" could not be sent", content.subject(), e);
        }
    }

    private void send(String to, EmailContent content) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(emailSenderUsername, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(content.html(), true);

            emailSender.send(message);
        } catch (Exception e) {
            throw new EmailNotSendException(e);
        }
    }

}
