package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;

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

    @Override
    public Account create(Account account) {
        accountCheckerService.pseudoIsAvailable(account);
        accountCheckerService.emailIsAvailable(account);

        Account toSave = accountModifierService.encodePassword(account)
                .withVerificationCode(RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH))
                .withVerificationExpiresAt(clock.instant().plus(verificationCodeTtl));

        Account savedAccount = spi.save(toSave)
                .getOrElseThrow(DataNotSaveException::new);

        try {
            emailSenderSpi.sendAccountValidationEmail(savedAccount);
        } catch (EmailNotSendException e) {
            // The account exists: failing here would leave a pseudo and an address taken by someone who
            // cannot log in. The person can ask for the mail again.
            log.warn("The validation e-mail of account {} could not be sent", savedAccount.getId(), e);
        }

        return savedAccount;
    }

}
