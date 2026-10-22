package fr.barlords.dndgateapi.server.adapter;


import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.AccountEntityMapper;
import fr.barlords.dndgateapi.server.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AccountDatabaseAdapter implements AccountPersistenceSpi {

    private static final Set<String> EMAIL_CONSTRAINTS = Set.of("accounts_email_key", "accounts_email_lower_key");

    private static final Set<String> PSEUDO_CONSTRAINTS = Set.of("accounts_pseudo_key", "accounts_pseudo_lower_key");

    private static final Pattern VIOLATED_CONSTRAINT = Pattern.compile("violates unique constraint \"([^\"]+)\"");

    private final AccountRepository repository;

    @Override
    @Transactional
    public Account save(Account o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return AccountEntityMapper.toDomain(repository.saveAndFlush(AccountEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous registrations: the unique index of the address or of the pseudo refused the second
            String constraint = constraintName(e);
            if (EMAIL_CONSTRAINTS.contains(constraint)) {
                throw new EmailAlreadyUsedException();
            }
            if (PSEUDO_CONSTRAINTS.contains(constraint)) {
                throw new PseudoAlreadyUsedException();
            }
            throw e;
        }
    }

    /**
     * The name of the constraint that refused the row, never read from the free text of the message: that text also
     * holds the refused value ("Key (pseudo)=(accounts_email_key)"), which a person chooses.
     */
    private static String constraintName(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        // Only the quoted name after the fixed wording, never the detail line
        Matcher matcher = VIOLATED_CONSTRAINT.matcher(String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage()));
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    @Transactional
    public Optional<Account> findById(UUID id) {
        return repository.findById(id).map(AccountEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Account> findByEmail(String email) {
        return repository.findByEmail(email).map(AccountEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Account> findByPseudo(String pseudo) {
        return repository.findByPseudoIgnoreCase(pseudo).map(AccountEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public List<Account> findAllByPendingEmail(String pendingEmail) {
        return repository.findAllByPendingEmail(pendingEmail).stream().map(AccountEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public Optional<Account> findByVerificationCode(String code) {
        return repository.findByVerificationCode(code).map(AccountEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Account update(Account o) {
        return save(o);
    }

}
