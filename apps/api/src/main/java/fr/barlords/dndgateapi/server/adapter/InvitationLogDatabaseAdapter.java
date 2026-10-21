package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi;
import fr.barlords.dndgateapi.server.entity.SentInvitationEntity;
import fr.barlords.dndgateapi.server.repository.SentInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationLogDatabaseAdapter implements InvitationLogSpi {

    private final SentInvitationRepository repository;

    @Override
    @Transactional
    public void record(UUID senderId, UUID recipientId, UUID adventureId, Instant sentAt) {
        repository.save(SentInvitationEntity.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .recipientId(recipientId)
                .adventureId(adventureId)
                .sentAt(sentAt)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instant> findSentAtBySenderSince(UUID senderId, Instant since) {
        return repository.findSentAtBySenderSince(senderId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLastSentToRecipientSince(UUID recipientId, UUID adventureId, Instant since) {
        return repository.findLastSentAt(recipientId, adventureId, since);
    }

    @Override
    @Transactional
    public void deleteSentBefore(Instant before) {
        repository.deleteSentBefore(before);
    }

}
