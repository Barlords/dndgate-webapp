package fr.barlords.dndgateapi.server.repository;


import fr.barlords.dndgateapi.server.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByEmail(String email);

    Optional<AccountEntity> findByPseudoIgnoreCase(String pseudo);

    List<AccountEntity> findAllByPendingEmail(String pendingEmail);


}
