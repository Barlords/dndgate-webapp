package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountCredentialNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.port.in.account.AccountLoggerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Logs an account in. After too many consecutive failures from one client the account is locked for that client,
 * even for the right password (see {@link AccountPasswordVerifierService}).
 */
@Slf4j
public class AccountLoggerService implements AccountLoggerApi {

    private final AccountPersistenceSpi spi;

    private final PasswordHasherSpi passwordHasher;

    private final AccountPasswordVerifierService passwordVerifier;

    private final SessionIssuerService sessionIssuer;

    private final AccountCheckerService accountCheckerService;

    /** Compared against when the pseudo is unknown, so that the answer takes the same time. */
    private final String unknownAccountHash;

    public AccountLoggerService(
            AccountPersistenceSpi spi,
            PasswordHasherSpi passwordHasher,
            AccountPasswordVerifierService passwordVerifier,
            SessionIssuerService sessionIssuer,
            AccountCheckerService accountCheckerService
    ) {
        this.spi = spi;
        this.passwordHasher = passwordHasher;
        this.passwordVerifier = passwordVerifier;
        this.sessionIssuer = sessionIssuer;
        this.accountCheckerService = accountCheckerService;
        this.unknownAccountHash = passwordHasher.hash("password-of-an-unknown-account");
    }

    @Override
    public AccountSession login(String pseudo, String password, ClientAddress client) {
        Optional<Account> found = spi.findByPseudo(pseudo);

        if (found.isEmpty()) {
            passwordHasher.matches(password, unknownAccountHash);
            throw new AccountCredentialNotFoundException();
        }

        Account account = passwordVerifier.verify(found.get(), password, client, AccountCredentialNotFoundException::new);

        accountCheckerService.accountIsActivated(account);

        return sessionIssuer.openSession(account);
    }

}
