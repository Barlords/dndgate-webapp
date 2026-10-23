package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.MagicItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface MagicItemRepository extends JpaRepository<MagicItemEntity, UUID> {

    List<MagicItemEntity> findByOwner_IdOrderByNameAsc(UUID ownerId);

    long countByOwner_Id(UUID ownerId);
}
