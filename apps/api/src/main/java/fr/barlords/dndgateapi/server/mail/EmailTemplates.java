package fr.barlords.dndgateapi.server.mail;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts of the e-mails, read from {@code mail/emails_<language>.properties}.
 * <p>
 * {@code {name}} is replaced in one pass: a value that itself contains {@code {other}} stays as it is, it is
 * never replaced a second time.
 */
class EmailTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)}");

    private final ResourceBundle bundle;

    EmailTemplates() {
        this(Locale.FRENCH);
    }

    EmailTemplates(Locale locale) {
        this.bundle = ResourceBundle.getBundle("mail/emails", locale);
    }

    String render(String key, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(bundle.getString(key));
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("The text " + key + " needs a value for {" + matcher.group(1) + "}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
