package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.HistoricalNoteEntityMapper;
import fr.barlords.dndgateapi.server.repository.HistoricalNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoricalNoteDatabaseAdapter implements HistoricalNotePersistenceSpi {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.asc("id"));

    private final HistoricalNoteRepository repository;

    @Override
    @Transactional
    public HistoricalNote save(HistoricalNote o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return HistoricalNoteEntityMapper.toDomain(repository.saveAndFlush(HistoricalNoteEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }

    @Override
    @Transactional
    public Optional<HistoricalNote> findById(UUID id) {
        return repository.findById(id).map(HistoricalNoteEntityMapper::toDomain);
    }


    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Page<HistoricalNote> findByAdventureId(UUID adventureId, PageRequest request) {
        return PageMapper.toDomain(repository.findByAdventure_Id(adventureId, PageMapper.toPageable(request, NEWEST_FIRST)), request,
                HistoricalNoteEntityMapper::toDomain);
    }


    @Override
    @Transactional(readOnly = true)
    public long countByAdventureId(UUID adventureId) {
        return repository.countByAdventure_Id(adventureId);
    }
}
