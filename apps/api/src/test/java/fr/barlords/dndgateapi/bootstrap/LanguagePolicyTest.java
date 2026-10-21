package fr.barlords.dndgateapi.bootstrap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The language of the project: code, comments, logs and the messages of the API are in English.
 * The only French is what a person reads in an e-mail, and it lives in {@code src/main/resources/mail}.
 */
class LanguagePolicyTest {

    private static final Pattern ACCENT = Pattern.compile("[àâäçéèêëîïôöûùüÀÂÇÉÈÊËÎÏÔÛÙ]");

    /** Frequent French words that are not English ones: catches French text written without any accent. */
    private static final Pattern FRENCH_WORD = Pattern.compile(
            "\\b(les|des|une|pour|sinon|avec|dans|sans|sont|mais|que|est|pas|cette|vous|nous|toute|tous|par)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void the_java_code_of_the_application_is_in_english() throws IOException {
        List<String> found;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            found = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::accentedLines)
                    .toList();
        }

        Assertions.assertEquals(List.of(), found, "French text in the code: it belongs in src/main/resources/mail");
    }

    private Stream<String> accentedLines(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(i -> ACCENT.matcher(lines.get(i)).find() || FRENCH_WORD.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1) + " " + lines.get(i).strip());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
