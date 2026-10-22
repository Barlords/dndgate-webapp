package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountVerificationResenderApi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class AccountVerificationResenderService implements AccountVerificationResenderApi {

    private final PendingSignupPersistenceSpi pendingSignups;

    private final EmailingSpi emailingSpi;

    private final Clock clock;

    /** How long a code works. */
    private final Duration codeTtl;

    /** Minimum time between two mails to the same address. */
    private final Duration cooldown;

    @Override
    public void resend(String email) {
        pendingSignups.findByEmail(email).ifPresent(this::sendNewCode);
    }

    private void sendNewCode(PendingSignup signup) {
        Instant now = clock.instant();
        String code = RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH);
        PendingSignup renewed = signup
                .withCodeHash(PendingSignup.hashOf(code))
                .withCreatedAt(now)
                .withExpiresAt(now.plus(codeTtl));

        // One statement decides: a signup younger than the cooldown is kept as it is, in silence
        if (!pendingSignups.renewUnlessRecent(renewed.getEmail(), renewed.getCodeHash(), now, renewed.getExpiresAt(),
                now.minus(cooldown))) {
            return;
        }

        try {
            emailingSpi.sendSignupConfirmationEmail(renewed, code);
        } catch (EmailNotSendException e) {
            // the caller must not learn anything about this address: the person can ask again
            log.warn("The confirmation e-mail of pending signup {} could not be sent", renewed.getId(), e);
        }
    }

}
