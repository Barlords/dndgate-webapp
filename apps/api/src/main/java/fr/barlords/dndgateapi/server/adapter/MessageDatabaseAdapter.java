package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.server.mapper.PageMapper;
import org.springframework.data.domain.Sort;

import fr.barlords.dndgateapi.domain.ApplicationError;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.MessageEntityMapper;
import fr.barlords.dndgateapi.server.repository.MessageRepository;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.vavr.API.Try;

@Service
@RequiredArgsConstructor
public class MessageDatabaseAdapter implements MessagePersistenceSpi {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sendDate"), Sort.Order.asc("id"));

    private final MessageRepository repository;

    @Override
    @Transactional
    public Either<ApplicationError, Message> save(Message o) {
        return Try(() -> repository.save(MessageEntityMapper.fromDomain(o)))
                .toEither()
                .mapLeft(throwable -> new ApplicationError("Error while writing to the database", null, o, throwable))
                .map(MessageEntityMapper::toDomain);
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
    @Transactional
    public List<Message> findBySenderId(UUID senderId) {
        return repository.findMessageEntitiesBySenderIdOrderBySendDate(senderId).stream().map(MessageEntityMapper::toDomain).toList();
    }
}
