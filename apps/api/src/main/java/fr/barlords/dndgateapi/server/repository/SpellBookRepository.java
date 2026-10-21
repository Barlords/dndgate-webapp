package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SpellBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SpellBookRepository extends JpaRepository<SpellBookEntity, UUID> {

    List<SpellBookEntity> findByOwner_Id(UUID owner_id);

}
