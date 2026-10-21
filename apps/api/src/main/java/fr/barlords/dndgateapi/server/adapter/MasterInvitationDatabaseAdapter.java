package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterInvitation;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.MasterInvitationEntityMapper;
import fr.barlords.dndgateapi.server.repository.MasterInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterInvitationDatabaseAdapter implements MasterInvitationPersistenceSpi {

    private final MasterInvitationRepository repository;

    @Override
    @Transactional
    public MasterInvitation replace(MasterInvitation invitation) {
        repository.deleteByAdventureId(invitation.getAdventureId());
        try {
            return MasterInvitationEntityMapper.toDomain(repository.saveAndFlush(MasterInvitationEntityMapper.fromDomain(invitation)));
        } catch (DataIntegrityViolationException e) {
            // Someone else created an invitation for the same adventure at the same moment
            throw new AdventureConcurrentModificationException(e);
        }
    }

    @Override
    @Transactional
    public Optional<MasterInvitation> findByCodeHash(String codeHash) {
        return repository.findByCodeHash(codeHash).map(MasterInvitationEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean consume(UUID invitationId) {
        return repository.deleteByInvitationId(invitationId) == 1;
    }

    @Override
    @Transactional
    public void deleteByAdventureId(UUID adventureId) {
        repository.deleteByAdventureId(adventureId);
    }
}
