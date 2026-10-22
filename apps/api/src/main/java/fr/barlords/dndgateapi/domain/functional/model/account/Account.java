package fr.barlords.dndgateapi.domain.functional.model.account;

import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class Account {

    public static final int VERIFICATION_CODE_LENGTH = 24;

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    String email;

    @With
    String password;

    @With
    String pseudo;

    @Builder.Default
    @With
    boolean activated = false;

    /** The secret sent by e-mail to prove that the address belongs to the person. Null once the address is verified. */
    @Builder.Default
    @With
    String verificationCode = RandomStringGenerator.generateAlphanumericString(VERIFICATION_CODE_LENGTH);

    /** After this instant the code no longer works and a new one has to be requested. */
    @With
    Instant verificationExpiresAt;

    /**
     * The address the owner asked to switch to. It replaces {@link #email} only once its owner followed the link
     * sent to it; until then the account keeps being identified by the current address.
     */
    @With
    String pendingEmail;

    /** The secret sent to {@link #pendingEmail}. Not the registration code: one cannot confirm the other. */
    @With
    String pendingEmailCode;

    @With
    Instant pendingEmailExpiresAt;

    /** Set when the account was anonymized: it keeps its id, but nothing identifies the person anymore. */
    @With
    Instant anonymizedAt;

    /** Version read from the storage, null while the account has never been saved: it detects concurrent changes. */
    @With
    Long version;

    /**
     * True when the code is the one that was sent and has not expired. The comparison takes the same time
     * whatever the number of correct characters, so that it cannot be used to guess the code one character at a time.
     */
    public boolean hasValidVerificationCode(String code, Instant now) {
        if (code == null || verificationCode == null || verificationExpiresAt == null || !verificationExpiresAt.isAfter(now)) {
            return false;
        }
        return MessageDigest.isEqual(
                verificationCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
    }

    /** Same rules as {@link #hasValidVerificationCode}, for the code of the address change. */
    public boolean hasValidPendingEmailCode(String code, Instant now) {
        if (code == null || pendingEmail == null || pendingEmailCode == null
                || pendingEmailExpiresAt == null || !pendingEmailExpiresAt.isAfter(now)) {
            return false;
        }
        return MessageDigest.isEqual(
                pendingEmailCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

}
