package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import fr.barlords.dndgateapi.server.entity.PlanningEntity;
import fr.barlords.dndgateapi.server.mapper.PlanningEntityMapper;
import fr.barlords.dndgateapi.server.repository.PlanningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanningDatabaseAdapter implements PlanningPersistenceSpi {

    private final PlanningRepository repository;

    @Override
    @Transactional
    public Planning save(Planning o){
        return PlanningEntityMapper.toDomain(repository.save(PlanningEntityMapper.fromDomain(o)));
    }

    @Override
    @Transactional
    public Optional<Planning> findByAdventureId(UUID adventureId) {
        List<PlanningEntity> entities = repository.findByAdventureId(adventureId);
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        PlanningEntity entity = entities.get(0);
        return Optional.of(PlanningEntityMapper.toDomain(entity));
    }


    @Override
    @Transactional
    public Optional<Planning> findById(UUID id) {
        return repository.findById(id).map(PlanningEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
