package fr.barlords.dndgateapi.domain.port.out;

public interface PasswordHasherSpi {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);

}
