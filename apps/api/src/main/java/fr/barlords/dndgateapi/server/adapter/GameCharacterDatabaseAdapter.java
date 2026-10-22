package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.GameCharacterEntityMapper;
import fr.barlords.dndgateapi.server.repository.GameCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameCharacterDatabaseAdapter implements GameCharacterPersistenceSpi {

    private final GameCharacterRepository repository;

    @Override
    @Transactional
    public GameCharacter save(GameCharacter o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return GameCharacterEntityMapper.toDomain(repository.saveAndFlush(GameCharacterEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }

    @Override
    @Transactional
    public Optional<GameCharacter> findById(UUID id) {
        return repository.findById(id).map(GameCharacterEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public List<GameCharacter> findByOwnerId(UUID ownerId) {
        return repository.findByOwner_Id(ownerId).stream().map(GameCharacterEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public List<GameCharacter> findByAdventureId(UUID adventureId) {
        return repository.findByAdventure_Id(adventureId).stream().map(GameCharacterEntityMapper::toDomain).toList();
    }


    @Override
    @Transactional(readOnly = true)
    public long countByOwnerIdAndAdventureId(UUID ownerId, UUID adventureId) {
        return repository.countByOwner_IdAndAdventure_Id(ownerId, adventureId);
    }
}
