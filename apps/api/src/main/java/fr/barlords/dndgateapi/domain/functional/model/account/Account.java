package fr.barlords.dndgateapi.domain.functional.model.account;

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

    /** Length of the codes sent by e-mail (signup confirmation, address change). */
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
     * True when the code is the one sent to the pending address and has not expired. The comparison takes the same time
     * whatever the number of correct characters, so that it cannot be used to guess the code one character at a time.
     */
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
