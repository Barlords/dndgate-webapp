package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
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

    private final EmailingSpi emailSenderSpi;

    private final Clock clock;

    /** How long the code sent by e-mail works. */
    private final Duration verificationCodeTtl;

    private final SignupNoticeLogSpi noticeLog;

    /** How long a notice blocks the next one for the same address. */
    private final Duration noticeWindow;

    @Override
    public void create(Account account) {
        // Hashed before the address is looked at: both answers cost the same time, the hash being the slow part
        Account encoded = accountModifierService.encodePassword(account);

        // The address comes first: when it is taken the answer is the same whatever the pseudo
        Optional<Account> owner = spi.findByEmail(account.getEmail());
        if (owner.isPresent()) {
            tellTheOwner(owner.get());
            return;
        }

        accountCheckerService.pseudoIsAvailable(account);

        Account toSave = encoded
                .withVerificationCode(RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH))
                .withVerificationExpiresAt(clock.instant().plus(verificationCodeTtl));

        Account savedAccount;
        try {
            savedAccount = spi.save(toSave);
        } catch (EmailAlreadyUsedException e) {
            // Somebody registered the same address between the look-up and the insert: same answer as above
            spi.findByEmail(account.getEmail()).ifPresent(this::tellTheOwner);
            return;
        }

        try {
            emailSenderSpi.sendAccountValidationEmail(savedAccount);
        } catch (EmailNotSendException e) {
            // The account exists: failing here would leave a pseudo and an address taken by someone who
            // cannot log in. The person can ask for the mail again.
            log.warn("The validation e-mail of account {} could not be sent", savedAccount.getId(), e);
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
