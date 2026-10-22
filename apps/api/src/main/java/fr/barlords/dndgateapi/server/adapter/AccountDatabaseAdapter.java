package fr.barlords.dndgateapi.server.adapter;


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
        return AccountEntityMapper.toDomain(repository.save(AccountEntityMapper.fromDomain(o)));
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
        return AccountEntityMapper.toDomain(repository.save(AccountEntityMapper.fromDomain(o)));
    }

}
