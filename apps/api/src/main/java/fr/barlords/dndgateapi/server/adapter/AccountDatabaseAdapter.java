package fr.barlords.dndgateapi.server.adapter;


import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDatabaseAdapter implements AccountPersistenceSpi {

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
            String message = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (message.contains("accounts_email")) {
                throw new EmailAlreadyUsedException();
            }
            if (message.contains("accounts_pseudo")) {
                throw new PseudoAlreadyUsedException();
            }
            throw e;
        }
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
