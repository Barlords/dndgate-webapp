package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.SpellBookEntityMapper;
import fr.barlords.dndgateapi.server.repository.SpellBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpellBookDatabaseAdapter implements SpellBookPersistenceSpi {

    private final SpellBookRepository repository;

    @Override
    @Transactional
    public SpellBook save(SpellBook o) {
        return SpellBookEntityMapper.toDomain(repository.save(SpellBookEntityMapper.fromDomain(o)));
    }

    @Override
    @Transactional
    public Optional<SpellBook> findById(UUID id) {
        return repository.findById(id).map(SpellBookEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public List<SpellBook> findByOwnerId(UUID ownerId) {
        return repository.findByOwner_Id(ownerId).stream().map(SpellBookEntityMapper::toDomain).toList();
    }


    @Override
    @Transactional(readOnly = true)
    public long countByOwnerId(UUID ownerId) {
        return repository.countByOwner_Id(ownerId);
    }
}
