package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AccountCreatorService implements AccountCreatorApi {

    private final AccountPersistenceSpi spi;

    private final AccountCheckerService accountCheckerService;

    private final AccountModifierService accountModifierService;

    private final PendingSignupPersistenceSpi pendingSignups;

    private final EmailingSpi emailSenderSpi;

    private final Clock clock;

    /** How long the code sent by e-mail works. */
    private final Duration verificationCodeTtl;

    /** Minimum time between two confirmation mails for the same address. */
    private final Duration resendCooldown;

    private final SignupNoticeLogSpi noticeLog;

    /** How long a notice blocks the next one for the same address. */
    private final Duration noticeWindow;

    @Override
    public void create(Account account) {
        // The pseudo comes first and is public: a taken pseudo answers 409 whatever the address, so the answer
        // can never tell whether the address is registered (checking the address first would be an oracle).
        // Only accounts count: the pseudo of a pending signup is not reserved, or it would be a way to probe.
        accountCheckerService.pseudoIsAvailable(account);

        // Hashed before the address is looked at: both answers cost the same time, the hash being the slow part
        Account encoded = accountModifierService.encodePassword(account);

        Optional<Account> owner = spi.findByEmail(account.getEmail());
        if (owner.isPresent()) {
            tellTheOwner(owner.get());
            return;
        }

        Instant now = clock.instant();
        String code = RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH);
        PendingSignup signup = PendingSignup.builder()
                .email(account.getEmail())
                .pseudo(account.getPseudo())
                .passwordHash(encoded.getPassword())
                .codeHash(PendingSignup.hashOf(code))
                .createdAt(now)
                .expiresAt(now.plus(verificationCodeTtl))
                .build();

        // A signup younger than the cooldown is kept as it is, in silence: the caller learns nothing either way
        if (!pendingSignups.saveUnlessRecent(signup, now.minus(resendCooldown))) {
            return;
        }

        try {
            emailSenderSpi.sendSignupConfirmationEmail(signup, code);
        } catch (EmailNotSendException e) {
            // The answer must not depend on it: the person can sign up again once the cooldown has passed
            log.warn("The confirmation e-mail of a pending signup could not be sent", e);
        }
    }

    /** At most one notice per address per window: the answer to the caller never depends on it. */
    private void tellTheOwner(Account owner) {
        Instant now = clock.instant();
        if (!noticeLog.claim(owner.getEmail(), now, now.minus(noticeWindow))) {
            return;
        }
        try {
            emailSenderSpi.sendSignupAttemptEmail(owner);
        } catch (EmailNotSendException e) {
            log.warn("The notice of a registration attempt for account {} could not be sent", owner.getId(), e);
        }
    }

}
