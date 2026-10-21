package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Checks the password of an account, wherever it is asked: to log in, to confirm the deletion of the account,
 * to change the password. A wrong password always counts against the same limit, so that nobody can guess
 * a password through the "sensitive" endpoints once an access token is stolen.
 */
@RequiredArgsConstructor
public class AccountPasswordVerifierService {

    private final AccountPersistenceSpi spi;

    private final PasswordHasherSpi passwordHasher;

    private final Clock clock;

    private final int maxFailedAttempts;

    private final Duration lockDuration;

    /**
     * @param onWrongPassword what to throw when the password is wrong (the caller decides the kind of error)
     * @return the account with its failure counters up to date
     * @throws AccountLockedException when too many attempts failed recently, even if this password is right
     */
    public Account verify(Account account, String rawPassword, Supplier<? extends RuntimeException> onWrongPassword) {
        Instant now = clock.instant();

        if (account.isLockedAt(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }

        if (account.getLockedUntil() != null) {
            // the lock has expired: start counting from zero again
            spi.resetFailedLogins(account.getId());
            account = account.withFailedLoginAttempts(0).withLockedUntil(null);
        }

        if (!passwordHasher.matches(rawPassword, account.getPassword())) {
            spi.registerFailedLogin(account.getId(), maxFailedAttempts, now.plus(lockDuration));
            throw onWrongPassword.get();
        }

        if (account.getFailedLoginAttempts() > 0) {
            spi.resetFailedLogins(account.getId());
            account = account.withFailedLoginAttempts(0);
        }

        return account;
    }

}
