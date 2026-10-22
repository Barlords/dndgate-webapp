package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimAlreadyOpenException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.MasterClaimEntityMapper;
import fr.barlords.dndgateapi.server.repository.MasterClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterClaimDatabaseAdapter implements MasterClaimPersistenceSpi {

    private final MasterClaimRepository repository;

    @Override
    @Transactional
    public MasterClaim save(MasterClaim claim) {
        try {
            // Flushed here so that a conflict is detected now, not later when the transaction commits
            return MasterClaimEntityMapper.toDomain(repository.saveAndFlush(MasterClaimEntityMapper.fromDomain(claim)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new AdventureConcurrentModificationException(e);
        } catch (DataIntegrityViolationException e) {
            // The only constraint that can be broken here: one open claim per adventure
            throw new MasterClaimAlreadyOpenException();
        }
    }

    @Override
    @Transactional
    public Optional<MasterClaim> findOpenByAdventureId(UUID adventureId) {
        return repository.findByAdventureIdAndStatus(adventureId, MasterClaimStatus.OPEN).map(MasterClaimEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public List<MasterClaim> findOpenByCandidateId(UUID candidateId) {
        return repository.findAllByCandidateIdAndStatus(candidateId, MasterClaimStatus.OPEN).stream()
                .map(MasterClaimEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteByAdventureId(UUID adventureId) {
        repository.deleteByAdventureId(adventureId);
    }
}
