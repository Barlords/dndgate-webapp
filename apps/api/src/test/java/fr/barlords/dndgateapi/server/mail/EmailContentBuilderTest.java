package fr.barlords.dndgateapi.server.mail;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferEvent;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmailContentBuilderTest {

    private final EmailContentBuilder builder = new EmailContentBuilder("https://dndgate.example.com");

    private Account account(String pseudo, String email, String code) {
        return Account.builder().pseudo(pseudo).email(email).verificationCode(code).build();
    }

    // ================= the link of the verification mail =================

    @Test
    void the_link_leads_to_the_web_site_with_the_address_and_the_code() {
        EmailContent mail = builder.accountValidation(account("Gandalf", "gandalf@middle.earth", "abc123"));

        Assertions.assertTrue(mail.html().contains("href=\"https://dndgate.example.com/verify-email?email=gandalf%40middle.earth&amp;code=abc123\""),
                mail.html());
        Assertions.assertEquals("Validation de votre compte", mail.subject());
    }

    @Test
    void a_plus_sign_in_the_address_is_encoded_so_that_it_is_not_read_as_a_space() {
        EmailContent mail = builder.accountValidation(account("Gandalf", "gandalf+rpg@middle.earth", "abc123"));

        Assertions.assertTrue(mail.html().contains("email=gandalf%2Brpg%40middle.earth"), mail.html());
        Assertions.assertFalse(mail.html().contains("gandalf+rpg"));
    }

    @Test
    void characters_that_would_break_a_query_string_are_encoded() {
        EmailContent mail = builder.accountValidation(account("Gandalf", "a&b=c#d@middle.earth", "x y&z"));

        Assertions.assertTrue(mail.html().contains("email=a%26b%3Dc%23d%40middle.earth"), mail.html());
        Assertions.assertTrue(mail.html().contains("code=x+y%26z"), mail.html());
    }

    @Test
    void a_trailing_slash_in_the_site_address_does_not_double_up() {
        EmailContent mail = new EmailContentBuilder("https://dndgate.example.com/")
                .accountValidation(account("Gandalf", "g@middle.earth", "abc"));

        Assertions.assertTrue(mail.html().contains("https://dndgate.example.com/verify-email?"));
        Assertions.assertFalse(mail.html().contains("com//verify"));
    }

    // ================= the confirmation of a signup =================

    private PendingSignup signup(String pseudo, String email) {
        return PendingSignup.builder().pseudo(pseudo).email(email).passwordHash("hash").codeHash("codehash").build();
    }

    @Test
    void the_signup_confirmation_links_to_the_web_site_with_the_address_and_the_clear_code() {
        EmailContent mail = builder.signupConfirmation(signup("Gandalf", "gandalf+rpg@middle.earth"), "abc123");

        Assertions.assertTrue(mail.html().contains(
                "href=\"https://dndgate.example.com/verify-email?email=gandalf%2Brpg%40middle.earth&amp;code=abc123\""), mail.html());
        Assertions.assertEquals("Confirmation de votre inscription", mail.subject());
        Assertions.assertFalse(mail.html().contains("codehash"), mail.html());
    }

    @Test
    void the_signup_confirmation_says_to_ignore_the_mail_when_the_person_did_not_ask_and_keeps_the_pseudo_as_text() {
        EmailContent mail = builder.signupConfirmation(signup("<script>alert(1)</script>", "g@middle.earth"), "abc");

        Assertions.assertTrue(mail.html().contains("ignorez simplement ce mail"), mail.html());
        Assertions.assertFalse(mail.html().contains("<script"), mail.html());
        Assertions.assertTrue(mail.html().contains("&lt;script&gt;"), mail.html());
        Assertions.assertEquals(1, mail.html().split("<a ", -1).length - 1, "the only link is the confirmation one");
    }

    // ================= the notice of a registration attempt =================

    @Test
    void the_notice_of_an_attempt_names_the_owner_and_carries_no_code_nor_link_to_verify() {
        EmailContent mail = builder.signupAttempt(Account.builder().pseudo("<b>Gandalf</b>").email("g@middle.earth")
                .verificationCode("abc123").build());

        Assertions.assertEquals("Tentative d'inscription avec votre adresse", mail.subject());
        Assertions.assertTrue(mail.html().contains("&lt;b&gt;Gandalf&lt;/b&gt;"), mail.html());
        Assertions.assertFalse(mail.html().contains("abc123"), mail.html());
        Assertions.assertFalse(mail.html().contains("verify-email"), mail.html());
        Assertions.assertFalse(mail.html().contains("validation"), "no invitation to ask for a validation mail: " + mail.html());
        Assertions.assertTrue(mail.html().contains("href=\"https://dndgate.example.com/login\""), mail.html());
    }

    // ================= the link of the address change mail =================

    private Account waitingFor(String pseudo, String newEmail, String code) {
        return Account.builder().pseudo(pseudo).email("old@middle.earth")
                .pendingEmail(newEmail).pendingEmailCode(code).build();
    }

    @Test
    void the_address_change_link_carries_the_new_address_and_its_own_code() {
        EmailContent mail = builder.emailChange(waitingFor("Gandalf", "new+rpg@middle.earth", "abc123"));

        Assertions.assertTrue(mail.html().contains(
                "href=\"https://dndgate.example.com/confirm-email?email=new%2Brpg%40middle.earth&amp;code=abc123\""), mail.html());
        Assertions.assertFalse(mail.html().contains("old@middle.earth"));
        Assertions.assertEquals("Confirmation de votre nouvelle adresse", mail.subject());
    }

    @Test
    void a_pseudo_with_markup_stays_text_in_the_address_change_mail() {
        EmailContent mail = builder.emailChange(waitingFor("<script>alert(1)</script>", "new@middle.earth", "abc"));

        Assertions.assertFalse(mail.html().contains("<script"), mail.html());
        Assertions.assertTrue(mail.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"), mail.html());
        Assertions.assertEquals(1, mail.html().split("<a ", -1).length - 1, "the only link is the confirmation one");
    }

    // ================= what comes from a person is never markup =================

    @Test
    void a_pseudo_with_markup_stays_text_in_the_verification_mail() {
        EmailContent mail = builder.accountValidation(
                account("<script>alert(1)</script><a href=\"https://evil.example\">click</a>", "g@middle.earth", "abc"));

        Assertions.assertFalse(mail.html().contains("<script"), mail.html());
        Assertions.assertFalse(mail.html().contains("evil.example\">"), mail.html());
        Assertions.assertTrue(mail.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"), mail.html());
        Assertions.assertEquals(1, mail.html().split("<a ", -1).length - 1, "the only link is the verification one");
    }

    @Test
    void quotes_cannot_close_an_attribute() {
        EmailContent mail = builder.accountValidation(account("\" onmouseover=\"alert(1)", "g@middle.earth", "abc"));

        Assertions.assertFalse(mail.html().contains("\" onmouseover=\""), mail.html());
        Assertions.assertTrue(mail.html().contains("&quot; onmouseover=&quot;alert(1)"), mail.html());
    }

    @Test
    void the_invitation_escapes_both_pseudos_and_the_code() {
        EmailContent mail = builder.invitation(
                account("<b>sender</b>", "s@middle.earth", "x"),
                account("<i>receiver</i>", "r@middle.earth", "x"),
                "<img src=x onerror=alert(1)>");

        Assertions.assertFalse(mail.html().contains("<b>"), mail.html());
        Assertions.assertFalse(mail.html().contains("<i>"), mail.html());
        Assertions.assertFalse(mail.html().contains("<img"), mail.html());
        Assertions.assertTrue(mail.html().contains("&lt;b&gt;sender&lt;/b&gt;"));
        Assertions.assertTrue(mail.html().contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    void the_invitation_contains_the_code_and_the_names() {
        EmailContent mail = builder.invitation(account("Gandalf", "g@middle.earth", "x"), account("Frodo", "f@middle.earth", "x"), "AbC123XyZ9");

        Assertions.assertTrue(mail.html().contains("Frodo"));
        Assertions.assertTrue(mail.html().contains("Gandalf vous a invité"));
        Assertions.assertTrue(mail.html().contains("AbC123XyZ9"));
        Assertions.assertEquals("Invitation de Gandalf", mail.subject());
    }

    @Test
    void the_subject_never_contains_a_line_break() {
        EmailContent mail = builder.invitation(
                account("Gandalf\r\nBcc: victim@example.com", "g@middle.earth", "x"), account("Frodo", "f@middle.earth", "x"), "code");

        Assertions.assertFalse(mail.subject().contains("\r"));
        Assertions.assertFalse(mail.subject().contains("\n"));
    }

    // ================= the e-mails about the game master =================

    private EmailContent master(MasterTransferEvent event, String adventureName, String other) {
        return builder.masterTransfer(account("Frodo", "f@middle.earth", "x"),
                new MasterTransferMessage(event, java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), adventureName, other));
    }

    @Test
    void every_event_has_a_subject_a_text_and_a_link_to_the_adventure() {
        for (MasterTransferEvent event : MasterTransferEvent.values()) {
            EmailContent mail = master(event, "The lost mine", "Sam");

            Assertions.assertFalse(mail.subject().isBlank(), event.name());
            Assertions.assertTrue(mail.html().contains("The lost mine"), event.name());
            Assertions.assertTrue(mail.html().contains("Bonjour Frodo"), event.name());
            Assertions.assertTrue(mail.html().contains("href=\"https://dndgate.example.com/adventures/11111111-2222-3333-4444-555555555555\""), event.name());
        }
    }

    @Test
    void the_text_says_who_is_concerned() {
        Assertions.assertTrue(master(MasterTransferEvent.CLAIM_OPENED, "Quest", "Sam").html().contains("Sam demande à devenir MJ"));
        Assertions.assertTrue(master(MasterTransferEvent.MASTER_CHANGED, "Quest", "Sam").html().contains("Sam est le nouveau MJ"));
        Assertions.assertTrue(master(MasterTransferEvent.MASTER_STEPPED_DOWN, "Quest", "Sam").html().contains("Sam a quitté le rôle de MJ"));
        Assertions.assertTrue(master(MasterTransferEvent.CLAIM_OPENED, "Quest", null).html().contains("Un joueur demande"));
        Assertions.assertTrue(master(MasterTransferEvent.CLAIM_OPENED, "Quest", "Sam").html().contains("7 jours"));
    }

    @Test
    void the_name_of_the_adventure_and_the_pseudos_stay_text() {
        EmailContent mail = master(MasterTransferEvent.CLAIM_OPENED, "<script>alert(1)</script>", "<b>Sam</b>");

        Assertions.assertFalse(mail.html().contains("<script"), mail.html());
        Assertions.assertFalse(mail.html().contains("<b>"), mail.html());
        Assertions.assertTrue(mail.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void the_subject_never_contains_a_line_break_even_with_a_hostile_name() {
        for (MasterTransferEvent event : MasterTransferEvent.values()) {
            EmailContent mail = master(event, "Quest\r\nBcc: victim@example.com", "Sam\r\nBcc: victim@example.com");

            Assertions.assertFalse(mail.subject().contains("\r"), event.name());
            Assertions.assertFalse(mail.subject().contains("\n"), event.name());
        }
    }
}

