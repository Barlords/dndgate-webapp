package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.LoginFailures;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checks the password of an account, wherever it is asked: to log in, to confirm the deletion of the account,
 * to change the password. A wrong password always counts against the same limit, so that nobody can guess
 * a password through the "sensitive" endpoints once an access token is stolen.
 * <p>
 * The lock is per (account, client): the pseudos are public, so a lock shared by every client would let anybody
 * keep a stranger out of their own account. Failures of one client never lock the others out. The failures
 * from all the clients are only counted to warn in the logs about a distributed guessing; nothing is blocked.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountPasswordVerifierService {

    private final LoginFailureSpi failures;

    private final PasswordHasherSpi passwordHasher;

    private final Clock clock;

    private final int maxFailedAttempts;

    private final Duration lockDuration;

    /** The number of different clients failing within the lock window from which a warning is logged. */
    private final int distinctClientsWarningThreshold;

    /**
     * @param client          who is asking, the lock is theirs only
     * @param onWrongPassword what to throw when the password is wrong (the caller decides the kind of error)
     * @return the account
     * @throws AccountLockedException when this client failed too many times recently, even if this password is right
     */
    public Account verify(Account account, String rawPassword, ClientAddress client,
                          Supplier<? extends RuntimeException> onWrongPassword) {
        Instant now = clock.instant();

        Optional<LoginFailures> previous = failures.find(account.getId(), client);

        if (previous.isPresent() && previous.get().isLockedAt(now)) {
            throw new AccountLockedException(previous.get().lockedUntil());
        }

        if (!passwordHasher.matches(rawPassword, account.getPassword())) {
            Instant windowStart = now.minus(lockDuration);
            int clientsBefore = failures.countClientsFailingSince(account.getId(), windowStart);
            failures.register(account.getId(), client, maxFailedAttempts, now.plus(lockDuration), now);
            warnWhenManyClientsFail(account, clientsBefore, windowStart);
            throw onWrongPassword.get();
        }

        if (previous.isPresent()) {
            failures.clear(account.getId(), client);
        }

        return account;
    }

    /** Only when the count crosses the threshold, not on every failure after that. */
    private void warnWhenManyClientsFail(Account account, int clientsBefore, Instant windowStart) {
        if (clientsBefore >= distinctClientsWarningThreshold) {
            return;
        }
        int clients = failures.countClientsFailingSince(account.getId(), windowStart);
        if (clients >= distinctClientsWarningThreshold) {
            log.warn("Possible distributed password guessing: account {} had failed logins from {} different clients in the last {}",
                    account.getId(), clients, lockDuration);
        }
    }

}
