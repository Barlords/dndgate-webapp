package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BcryptPasswordHasherAdapter implements PasswordHasherSpi {

    /** BCrypt only reads the first 72 bytes of a password. */
    private static final int MAX_BYTES = 72;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        if (tooLong(rawPassword)) {
            throw new IllegalArgumentException("A password cannot be longer than " + MAX_BYTES + " bytes");
        }
        return encoder.encode(rawPassword);
    }

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        if (hashedPassword == null || tooLong(rawPassword)) {
            // a password that BCrypt cannot even hash cannot be the right one
            return false;
        }
        return encoder.matches(rawPassword, hashedPassword);
    }

}
