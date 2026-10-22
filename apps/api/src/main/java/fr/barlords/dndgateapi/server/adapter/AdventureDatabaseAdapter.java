package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.AdventureEntityMapper;
import fr.barlords.dndgateapi.server.repository.AdventureNoteRepository;
import fr.barlords.dndgateapi.server.repository.AdventureRepository;
import fr.barlords.dndgateapi.server.repository.GameCharacterRepository;
import fr.barlords.dndgateapi.server.repository.HistoricalNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdventureDatabaseAdapter implements AdventurePersistenceSpi {

    /** A stable order is required for the pages not to overlap. */
    private static final Sort ORDER = Sort.by("name", "id");

    private final AdventureRepository repository;

    private final GameCharacterRepository gameCharacterRepository;

    private final HistoricalNoteRepository historicalNoteRepository;

    private final AdventureNoteRepository adventureNoteRepository;

    @Override
    @Transactional
    public Adventure save(Adventure o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return AdventureEntityMapper.toDomain(repository.saveAndFlush(AdventureEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new AdventureConcurrentModificationException(e);
        }
    }

    @Override
    @Transactional
    public Optional<Adventure> findById(UUID id) {
        return repository.findById(id).map(AdventureEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public List<Adventure> findAllByCreatorId(UUID creatorId) {
        return repository.findAllByCreatorId(creatorId).stream()
                .map(AdventureEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public List<Adventure> findAllByPlayersId(UUID players) {
        return repository.findAllByPlayersId(players).stream()
                .map(AdventureEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Page<Adventure> findVisibleTo(UUID accountId, PageRequest request) {
        return PageMapper.toDomain(repository.findVisibleTo(accountId, PageMapper.toPageable(request, ORDER)), request, AdventureEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Page<Adventure> findPageByCreatorId(UUID creatorId, PageRequest request) {
        return PageMapper.toDomain(repository.findByCreatorId(creatorId, PageMapper.toPageable(request, ORDER)), request, AdventureEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Page<Adventure> findPageByPlayerId(UUID playerId, PageRequest request) {
        return PageMapper.toDomain(repository.findPageByPlayerId(playerId, PageMapper.toPageable(request, ORDER)), request, AdventureEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Page<Adventure> findAvailableTo(UUID accountId, PageRequest request) {
        return PageMapper.toDomain(repository.findAvailableTo(accountId, PageMapper.toPageable(request, ORDER)), request, AdventureEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        // The characters and the notes point to their adventure through a foreign key that the
        // adventure does not know about: they have to be removed first, or the database refuses.
        gameCharacterRepository.deleteByAdventureId(id);
        historicalNoteRepository.deleteByAdventureId(id);
        adventureNoteRepository.deleteByAdventureId(id);

        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Adventure update(Adventure o) {
        return AdventureEntityMapper.toDomain(repository.saveAndFlush(AdventureEntityMapper.fromDomain(o)));
    }

    @Override
    @Transactional
    public Optional<Adventure> findByInvitationCode(String code) {
        return repository.findByInvitationCode(code)
                .map(AdventureEntityMapper::toDomain);
    }


    @Override
    @Transactional(readOnly = true)
    public long countActiveByOwnerId(UUID ownerId) {
        return repository.countActiveByCreatorId(ownerId);
    }
}
