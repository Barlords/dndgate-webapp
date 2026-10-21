package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AccountUpdaterService implements AccountUpdaterApi {

    private final AccountPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final AccountCheckerService accountCheckerService;

    private final AccountModifierService accountModifierService;

    private final AccountPasswordVerifierService passwordVerifier;

    private final AccountSessionRevokerApi accountSessionRevokerApi;

    private final EmailingSpi emailingSpi;

    private final Clock clock;

    /** How long the code sent to the new address works. */
    private final Duration emailChangeCodeTtl;

    @Override
    public Account update(UUID accountId, String currentPassword, AccountChanges changes) {
        Account previous = passwordVerifier.verify(
                currentAccountService.getAccountById(accountId), currentPassword, WrongPasswordException::new);

        Account patched = previous;
        boolean emailChangeRequested = false;

        if (changes.email() != null && !changes.email().equals(previous.getEmail())) {
            accountCheckerService.emailIsAvailable(previous.withEmail(changes.email()));
            // the account keeps its address until the owner of the new one proves it by the code sent there
            patched = patched
                    .withPendingEmail(changes.email())
                    .withPendingEmailCode(RandomStringGenerator.generateAlphanumericString(Account.VERIFICATION_CODE_LENGTH))
                    .withPendingEmailExpiresAt(clock.instant().plus(emailChangeCodeTtl));
            emailChangeRequested = true;
        }

        if (changes.pseudo() != null && !changes.pseudo().equals(previous.getPseudo())) {
            patched = patched.withPseudo(changes.pseudo());
            // a pseudo is unique whatever its case: changing only the case of one's own pseudo is not a conflict
            if (!changes.pseudo().equalsIgnoreCase(previous.getPseudo())) {
                accountCheckerService.pseudoIsAvailable(patched);
            }
        }

        boolean passwordChanged = changes.newPassword() != null && !changes.newPassword().equals(currentPassword);
        if (passwordChanged) {
            patched = accountModifierService.encodePassword(patched.withPassword(changes.newPassword()));
        }

        Account saved = spi.save(patched)
                .getOrElseThrow(DataNotSaveException::new);

        if (passwordChanged) {
            // whoever knew the old password must not keep a session
            accountSessionRevokerApi.logoutAll(accountId);
        }

        if (emailChangeRequested) {
            sendEmailChangeLink(saved);
        }

        return saved;
    }

    private void sendEmailChangeLink(Account account) {
        try {
            emailingSpi.sendEmailChangeEmail(account);
        } catch (EmailNotSendException e) {
            // the change is recorded: the person can ask again
            log.warn("The e-mail change confirmation of account {} could not be sent", account.getId(), e);
        }
    }
}
