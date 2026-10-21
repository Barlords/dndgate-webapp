package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterInvitation;
import fr.barlords.dndgateapi.server.entity.MasterInvitationEntity;

public interface MasterInvitationEntityMapper {

    static MasterInvitation toDomain(MasterInvitationEntity entity) {
        return MasterInvitation.builder()
                .id(entity.getId())
                .adventureId(entity.getAdventureId())
                .codeHash(entity.getCodeHash())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    static MasterInvitationEntity fromDomain(MasterInvitation domain) {
        return MasterInvitationEntity.builder()
                .id(domain.getId())
                .adventureId(domain.getAdventureId())
                .codeHash(domain.getCodeHash())
                .createdBy(domain.getCreatedBy())
                .createdAt(domain.getCreatedAt())
                .expiresAt(domain.getExpiresAt())
                .build();
    }
}
