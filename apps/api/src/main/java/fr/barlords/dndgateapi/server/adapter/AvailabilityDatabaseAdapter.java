package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.server.entity.AvailabilityEntity;
import fr.barlords.dndgateapi.server.mapper.AvailabilityEntityMapper;
import fr.barlords.dndgateapi.server.repository.AvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityDatabaseAdapter implements AvailabilityPersistenceSpi {

    private final AvailabilityRepository repository;

    @Override
    @Transactional
    public Availability save(Availability o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return AvailabilityEntityMapper.toDomain(repository.saveAndFlush(AvailabilityEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }


    @Override
    @Transactional
    public Optional<Availability> findById(UUID id) {
        return repository.findById(id)
                .map(AvailabilityEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByPlanningId(UUID planningId) {
        // entity by entity, so that the dates of each vote (a collection table) go with it
        repository.deleteAll(repository.findByPlanningId(planningId));
    }

    @Override
    @Transactional
    public List<Availability> findByPlanningId(UUID planningId) {
        List<AvailabilityEntity> entities = repository.findByPlanningId(planningId);
        return entities.stream()
                .map(AvailabilityEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<Availability> findByAccountIdAndPlanningId(UUID accountId, UUID planningId) {
        List<AvailabilityEntity> entities = repository.findByAccountIdAndPlanningId(accountId, planningId);
        return entities.stream()
                .map(AvailabilityEntityMapper::toDomain)
                .collect(Collectors.toList());
    }
}

