package fr.barlords.dndgateapi.domain.functional.model.adventure;

import java.time.Duration;

/**
 * How often invitation e-mails may be sent: at most {@code senderMax} per {@code senderWindow} for one sender,
 * and one per {@code recipientWindow} for one recipient and one adventure.
 */
public record InvitationLimits(int senderMax, Duration senderWindow, Duration recipientWindow) {

    public InvitationLimits {
        if (senderMax < 1) {
            throw new IllegalArgumentException("The invitation limit per sender must be at least 1");
        }
        if (senderWindow == null || senderWindow.isZero() || senderWindow.isNegative()) {
            throw new IllegalArgumentException("The invitation window per sender must be positive");
        }
        if (recipientWindow == null || recipientWindow.isZero() || recipientWindow.isNegative()) {
            throw new IllegalArgumentException("The invitation window per recipient must be positive");
        }
    }

    /** Records older than this can no longer change the outcome of a limit. */
    public Duration longestWindow() {
        return senderWindow.compareTo(recipientWindow) >= 0 ? senderWindow : recipientWindow;
    }

}
