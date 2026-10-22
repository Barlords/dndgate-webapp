package fr.barlords.dndgateapi.server.mail;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.MissingResourceException;
import java.util.Map;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void the_texts_are_read_in_utf_8_with_their_accents() {
        String html = templates.render("signup-confirmation.html", Map.of("pseudo", "x", "link", "y"));

        Assertions.assertEquals("Confirmation de votre inscription", templates.render("signup-confirmation.subject", Map.of()));
        Assertions.assertTrue(html.contains("Votre compte sera créé quand vous aurez confirmé votre adresse"), html);
        Assertions.assertTrue(html.contains("Si vous n'êtes pas à l'origine"), html);
    }

    @Test
    void the_values_are_put_in_place() {
        String text = templates.render("invitation.html", Map.of("receiver", "Frodo", "sender", "Gandalf", "code", "AbC"));

        Assertions.assertTrue(text.startsWith("Salutation Frodo,"), text);
        Assertions.assertTrue(text.contains("Gandalf vous a invité"), text);
        Assertions.assertTrue(text.contains("AbC."), text);
    }

    @Test
    void a_value_that_looks_like_a_placeholder_is_not_replaced_a_second_time() {
        String tricky = "$1 \\ {code}";

        String text = templates.render("invitation.html", Map.of("receiver", "{sender}", "sender", "Gandalf", "code", tricky));

        Assertions.assertTrue(text.startsWith("Salutation {sender},"), text);
        Assertions.assertTrue(text.contains(tricky + "."), text);
    }

    @Test
    void a_missing_value_is_an_error_not_a_mail_with_a_hole() {
        Assertions.assertThrows(IllegalStateException.class, () -> templates.render("invitation.html", Map.of("receiver", "Frodo")));
    }

    @Test
    void an_unknown_text_is_an_error() {
        Assertions.assertThrows(MissingResourceException.class, () -> templates.render("nothing.here", Map.of()));
    }
}
