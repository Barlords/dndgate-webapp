package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.service.account.AccountVerifierService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountVerifierApi;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The domain knows nothing about transactions: this decorator makes the confirmation of a signup one unit, so that the
 * signup is only used up when the account was created, and never the account without the signup being used up.
 * <p>
 * When the pseudo or the address turns out to be taken, the whole confirmation is rolled back (the signup comes back),
 * then the signup is forgotten in a transaction of its own: its code can never succeed anymore. This also covers the
 * race where the unique index of the accounts refuses the second of two simultaneous confirmations, which makes the
 * transaction unusable (nothing can be deleted inside it). A taken address is answered like an invalid code: it must
 * not tell which addresses have an account.
 */
public class TransactionalAccountVerifier implements AccountVerifierApi {

    private final AccountVerifierService delegate;

    private final TransactionTemplate transaction;

    public TransactionalAccountVerifier(AccountVerifierService delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void verify(String email, String code) {
        try {
            transaction.executeWithoutResult(status -> delegate.verify(email, code));
        } catch (PseudoAlreadyUsedException e) {
            forget(email, code);
            throw e;
        } catch (EmailAlreadyUsedException e) {
            forget(email, code);
            throw new InvalidVerificationCodeException();
        }
    }

    private void forget(String email, String code) {
        transaction.executeWithoutResult(status -> delegate.discard(email, code));
    }

}
