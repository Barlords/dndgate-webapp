package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.MagicItemEntityMapper;
import fr.barlords.dndgateapi.server.repository.MagicItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MagicItemDatabaseAdapter implements MagicItemPersistenceSpi {

    private final MagicItemRepository repository;

    @Override
    @Transactional
    public MagicItem save(MagicItem o) {
        // Flushed here so that a concurrent change is detected now, not later when the transaction commits
        try {
            return MagicItemEntityMapper.toDomain(repository.saveAndFlush(MagicItemEntityMapper.fromDomain(o)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(e);
        }
    }

    @Override
    @Transactional
    public Optional<MagicItem> findById(UUID id) {
        return repository.findById(id).map(MagicItemEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public List<MagicItem> findByOwnerId(UUID ownerId) {
        return repository.findByOwner_IdOrderByNameAsc(ownerId).stream().map(MagicItemEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOwnerId(UUID ownerId) {
        return repository.countByOwner_Id(ownerId);
    }
}
