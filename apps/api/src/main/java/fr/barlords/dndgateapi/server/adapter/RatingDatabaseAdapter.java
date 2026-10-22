package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.RatingEntityMapper;
import fr.barlords.dndgateapi.server.repository.RatingRepository;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingDatabaseAdapter implements RatingPersistenceSpi {

    private final RatingRepository repository;

    @Override
    @Transactional
    public Rating save(Rating o) {
        try {
            // Flushed here so that the unique (emitter, target) index answers now, not when the transaction commits
            return RatingEntityMapper.toDomain(repository.saveAndFlush(RatingEntityMapper.fromDomain(o)));
        } catch (DataIntegrityViolationException e) {
            // Two requests passed the "already rated" check together: the unique index let only one through
            throw new RatingAlreadyExistsException();
        }
    }

    @Override
    @Transactional
    public Optional<Rating> findById(UUID id) {
        return repository.findById(id).map(RatingEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Page<Rating> findByTargetedAccountId(UUID targetedAccountId, PageRequest request) {
        return PageMapper.toDomain(repository.findAllByTargetId(targetedAccountId, PageMapper.toPageable(request, Sort.by("id"))), request,
                RatingEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Rating> findByEmitterIdAndTargetedAccountId(UUID emitterId, UUID targetedAccountId) {
        return repository.findByEmitterIdAndTargetId(emitterId, targetedAccountId).map(RatingEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Double> computeAverageRatingOfAccount(UUID targetedAccountId) {
        return repository.findAverageRatingByAccountId(targetedAccountId);
    }

    @Override
    @Transactional
    public long countRatingsOfAccount(UUID targetedAccountId) {
        return repository.countByTargetId(targetedAccountId);
    }
}
