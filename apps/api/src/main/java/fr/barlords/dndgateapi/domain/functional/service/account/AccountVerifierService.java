package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.in.account.AccountVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

/**
 * Turns a pending signup into an account once its code is given back. Meant to run in one transaction (see
 * {@code TransactionalAccountVerifier}): the account is only kept if the signup was used up by this very call.
 */
@RequiredArgsConstructor
public class AccountVerifierService implements AccountVerifierApi {

    private final AccountPersistenceSpi accounts;

    private final PendingSignupPersistenceSpi pendingSignups;

    private final Clock clock;

    @Override
    public void verify(String email, String code) {
        PendingSignup signup = pendingSignups.findByEmail(email)
                .filter(found -> found.hasValidCode(code, clock.instant()))
                .orElseThrow(InvalidVerificationCodeException::new);

        // Using the signup up is what makes the code single-use: of several simultaneous confirmations, only the one
        // that removes the row goes on, the others find nothing left and see an invalid code
        if (!pendingSignups.deleteByEmailAndCodeHash(email, signup.getCodeHash())) {
            throw new InvalidVerificationCodeException();
        }

        // Nothing reserved the pseudo or the address while the link waited: they may have been taken since
        if (accounts.findByPseudo(signup.getPseudo()).isPresent()) {
            throw new PseudoAlreadyUsedException();
        }
        if (accounts.findByEmail(signup.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        }

        accounts.save(Account.builder()
                .email(signup.getEmail())
                .pseudo(signup.getPseudo())
                .password(signup.getPasswordHash())
                .activated(true)
                .build());
    }

    /**
     * Forgets the signup that holds this code, after a confirmation that was refused for good (the pseudo or the
     * address is taken): the code is dead, there is no point keeping the row. Only the signup of that code is removed,
     * so that nobody can destroy the signup of another person by guessing.
     */
    public void discard(String email, String code) {
        pendingSignups.deleteByEmailAndCodeHash(email, PendingSignup.hashOf(code));
    }

}
