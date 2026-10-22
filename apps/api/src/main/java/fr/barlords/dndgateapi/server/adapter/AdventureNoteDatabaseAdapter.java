package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import fr.barlords.dndgateapi.server.entity.AdventureNoteEntity;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.AdventureNoteEntityMapper;
import fr.barlords.dndgateapi.server.repository.AdventureNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdventureNoteDatabaseAdapter implements AdventureNotePersistenceSpi {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.asc("id"));

    private final AdventureNoteRepository repository;

    @Override
    @Transactional
    public AdventureNote save(AdventureNote o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return AdventureNoteEntityMapper.toDomain(repository.saveAndFlush(AdventureNoteEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }

    @Override
    @Transactional
    public Optional<AdventureNote> findById(UUID id) {
        return repository.findById(id).map(AdventureNoteEntityMapper::toDomain);
    }


    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Page<AdventureNote> findByAdventureId(UUID adventureId, boolean includePrivate, PageRequest request) {
        Pageable pageable = PageMapper.toPageable(request, NEWEST_FIRST);
        org.springframework.data.domain.Page<AdventureNoteEntity> found = includePrivate
                ? repository.findByAdventure_Id(adventureId, pageable)
                : repository.findByAdventure_IdAndNoteVisibility(adventureId, NoteVisibility.PUBLIC, pageable);

        return PageMapper.toDomain(found, request, AdventureNoteEntityMapper::toDomain);
    }


    @Override
    @Transactional(readOnly = true)
    public long countByAdventureId(UUID adventureId) {
        return repository.countByAdventure_Id(adventureId);
    }
}
