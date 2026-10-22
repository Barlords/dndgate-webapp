package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.service.account.AccountVerifierService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * A refused confirmation rolls back what it did (the signup is given back), so forgetting the signup has to happen
 * in a transaction of its own, after the rollback; and what the database refuses for a duplicate is told like the
 * checks of the service do.
 */
class TransactionalAccountVerifierTest {

    private final AccountVerifierService delegate = Mockito.mock(AccountVerifierService.class);

    private final PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);

    private TransactionalAccountVerifier verifier;

    @BeforeEach
    void setUp() {
        Mockito.when(transactionManager.getTransaction(Mockito.any())).thenReturn(Mockito.mock(TransactionStatus.class));
        verifier = new TransactionalAccountVerifier(delegate, transactionManager);
    }

    @Test
    void a_confirmation_is_one_transaction_that_commits() {
        verifier.verify("a@test.fr", "code");

        InOrder order = Mockito.inOrder(transactionManager, delegate);
        order.verify(transactionManager).getTransaction(Mockito.any());
        order.verify(delegate).verify("a@test.fr", "code");
        order.verify(transactionManager).commit(Mockito.any());
        Mockito.verify(delegate, Mockito.never()).discard(Mockito.any(), Mockito.any());
    }

    @Test
    void a_pseudo_taken_rolls_back_then_forgets_the_signup_in_a_new_transaction_and_is_told() {
        Mockito.doThrow(new PseudoAlreadyUsedException()).when(delegate).verify("a@test.fr", "code");

        Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> verifier.verify("a@test.fr", "code"));

        InOrder order = Mockito.inOrder(transactionManager, delegate);
        order.verify(transactionManager).rollback(Mockito.any());
        order.verify(transactionManager).getTransaction(Mockito.any());
        order.verify(delegate).discard("a@test.fr", "code");
        order.verify(transactionManager).commit(Mockito.any());
    }

    @Test
    void an_address_taken_is_an_invalid_code_and_the_signup_is_forgotten() {
        Mockito.doThrow(new EmailAlreadyUsedException()).when(delegate).verify("a@test.fr", "code");

        Assertions.assertThrows(InvalidVerificationCodeException.class, () -> verifier.verify("a@test.fr", "code"));

        Mockito.verify(delegate).discard("a@test.fr", "code");
    }

    @Test
    void an_invalid_code_forgets_nothing() {
        Mockito.doThrow(new InvalidVerificationCodeException()).when(delegate).verify("a@test.fr", "code");

        Assertions.assertThrows(InvalidVerificationCodeException.class, () -> verifier.verify("a@test.fr", "code"));

        Mockito.verify(delegate, Mockito.never()).discard(Mockito.any(), Mockito.any());
    }
}
