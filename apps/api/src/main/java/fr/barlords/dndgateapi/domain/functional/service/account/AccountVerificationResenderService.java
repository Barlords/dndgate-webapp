package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountVerificationResenderApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class AccountVerificationResenderService implements AccountVerificationResenderApi {

    private final AccountPersistenceSpi spi;

    private final EmailingSpi emailingSpi;

    private final Clock clock;

    /** How long a code works. */
    private final Duration codeTtl;

    /** Minimum time between two mails to the same address. */
    private final Duration cooldown;

    @Override
    public void resend(String email) {
        spi.findByEmail(email)
                .filter(account -> !account.isActivated() && !account.isAnonymized())
                .filter(account -> !sentRecently(account))
                .ifPresent(this::sendNewCode);
    }

    /** The code was created when the mail was sent: its expiry minus its lifetime is the sending time. */
    private boolean sentRecently(Account account) {
        if (account.getVerificationExpiresAt() == null) {
            return false;
        }
        Instant sentAt = account.getVerificationExpiresAt().minus(codeTtl);
        return sentAt.plus(cooldown).isAfter(clock.instant());
    }

    private void sendNewCode(Account account) {
        Account renewed = spi.save(account
                        .withVerificationCode(RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH))
                        .withVerificationExpiresAt(clock.instant().plus(codeTtl)))
                .getOrElseThrow(DataNotSaveException::new);

        try {
            emailingSpi.sendAccountValidationEmail(renewed);
        } catch (EmailNotSendException e) {
            // the caller must not learn anything about this address: the person can ask again
            log.warn("The validation e-mail of account {} could not be sent", renewed.getId(), e);
        }
    }

}
