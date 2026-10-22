package fr.barlords.dndgateapi.server.mail;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writes the e-mails from the texts of {@code mail/emails_fr.properties}. Everything that comes from a person
 * (pseudo, address, name of an adventure...) is escaped before it goes into the HTML, and encoded before it goes
 * into a link: a pseudo like "<a href=...>" must stay text, never become a link sent under the name of the platform.
 */
@Component
public class EmailContentBuilder {

    private final String webBaseUrl;

    private final EmailTemplates templates = new EmailTemplates();

    public EmailContentBuilder(@Value("${app.web-base-url}") String webBaseUrl) {
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    /** Goes to the address of a pending signup: the link carries the address and the clear code. */
    public EmailContent signupConfirmation(PendingSignup signup, String code) {
        String link = webBaseUrl + "/verify-email?email=" + encode(signup.getEmail())
                + "&code=" + encode(code);

        return new EmailContent(
                templates.render("signup-confirmation.subject", Map.of()),
                templates.render("signup-confirmation.html", Map.of(
                        "pseudo", escape(signup.getPseudo()),
                        "link", escape(link))));
    }

    /** Goes to the owner of an address somebody tried to register with: no code, only a way to log in. */
    public EmailContent signupAttempt(Account owner) {
        return new EmailContent(
                templates.render("signup-attempt.subject", Map.of()),
                templates.render("signup-attempt.html", Map.of(
                        "pseudo", escape(owner.getPseudo()),
                        "link", escape(webBaseUrl + "/login"))));
    }

    /** Goes to the new address: the link carries it, and the code that only its owner receives. */
    public EmailContent emailChange(Account account) {
        String link = webBaseUrl + "/confirm-email?email=" + encode(account.getPendingEmail())
                + "&code=" + encode(account.getPendingEmailCode());

        return new EmailContent(
                templates.render("email-change.subject", Map.of()),
                templates.render("email-change.html", Map.of(
                        "pseudo", escape(account.getPseudo()),
                        "link", escape(link))));
    }

    public EmailContent invitation(Account sender, Account receiver, String code) {
        return new EmailContent(
                templates.render("invitation.subject", Map.of("sender", singleLine(sender.getPseudo()))),
                templates.render("invitation.html", Map.of(
                        "receiver", escape(receiver.getPseudo()),
                        "sender", escape(sender.getPseudo()),
                        "code", escape(code))));
    }

    /** The e-mails about the game master of an adventure. */
    public EmailContent masterTransfer(Account receiver, MasterTransferMessage message) {
        String event = "master." + message.event().name();
        String link = webBaseUrl + "/adventures/" + message.adventureId();

        String subject = templates.render(event + ".subject", Map.of(
                "adventure", singleLine(message.adventureName()),
                "other", message.otherPseudo() == null ? templates.render("master.someone.subject", Map.of()) : singleLine(message.otherPseudo())));

        String body = templates.render(event + ".body", Map.of(
                "adventure", escape(message.adventureName()),
                "other", message.otherPseudo() == null ? templates.render("master.someone", Map.of()) : escape(message.otherPseudo())));

        // The body is already HTML (its values were escaped one step above): it goes in as it is
        String html = templates.render("master.html", Map.of(
                "pseudo", escape(receiver.getPseudo()),
                "body", body,
                "link", escape(link)));

        return new EmailContent(subject, html);
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }

    /** A "+" must become %2B: in a query string a bare "+" is read as a space (an address like name+tag@mail.com). */
    private static String encode(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
    }

    /** A header (the subject) must never contain a line break: it would let someone add headers to the mail. */
    private static String singleLine(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n]+", " ");
    }

}
