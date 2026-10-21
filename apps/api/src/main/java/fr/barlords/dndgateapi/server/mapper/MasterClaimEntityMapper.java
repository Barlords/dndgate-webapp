package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.server.entity.MasterClaimEntity;

import java.util.HashMap;

public interface MasterClaimEntityMapper {

    static MasterClaim toDomain(MasterClaimEntity entity) {
        return MasterClaim.builder()
                .id(entity.getId())
                .adventureId(entity.getAdventureId())
                .candidateId(entity.getCandidateId())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus())
                .votes(new HashMap<>(entity.getVotes()))
                .version(entity.getVersion())
                .build();
    }

    static MasterClaimEntity fromDomain(MasterClaim domain) {
        return MasterClaimEntity.builder()
                .id(domain.getId())
                .adventureId(domain.getAdventureId())
                .candidateId(domain.getCandidateId())
                .createdAt(domain.getCreatedAt())
                .expiresAt(domain.getExpiresAt())
                .status(domain.getStatus())
                .votes(new HashMap<>(domain.getVotes()))
                .version(domain.getVersion())
                .build();
    }
}
