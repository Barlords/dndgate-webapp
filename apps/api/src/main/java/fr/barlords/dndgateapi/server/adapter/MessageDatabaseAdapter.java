package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;

import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.MessageEntityMapper;
import fr.barlords.dndgateapi.server.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageDatabaseAdapter implements MessagePersistenceSpi {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sendDate"), Sort.Order.asc("id"));

    private final MessageRepository repository;

    @Override
    @Transactional
    public Message save(Message o) {
        return MessageEntityMapper.toDomain(repository.save(MessageEntityMapper.fromDomain(o)));
    }

    @Override
    @Transactional
    public Optional<Message> findById(UUID id) {
        return repository.findMessageEntityById(id).map(MessageEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Page<Message> findByAdventureId(UUID adventureId, PageRequest request) {
        return PageMapper.toDomain(repository.findByAdventureId(adventureId, PageMapper.toPageable(request, NEWEST_FIRST)), request, MessageEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByAdventureId(UUID adventureId) {
        repository.deleteByAdventureId(adventureId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByAdventureId(UUID adventureId) {
        return repository.countByAdventureId(adventureId);
    }

    @Override
    @Transactional
    public List<Message> findBySenderId(UUID senderId) {
        return repository.findMessageEntitiesBySenderIdOrderBySendDate(senderId).stream().map(MessageEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBySenderIdSince(UUID senderId, Instant since) {
        return repository.countBySenderIdSince(senderId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findOldestSendDateBySenderIdSince(UUID senderId, Instant since) {
        return repository.findOldestSendDateBySenderIdSince(senderId, since);
    }
}
