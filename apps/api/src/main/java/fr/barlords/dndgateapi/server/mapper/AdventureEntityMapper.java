package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.server.entity.AdventureEntity;

import java.util.ArrayList;


public interface AdventureEntityMapper {
    static Adventure toDomain(AdventureEntity entity) {
        return Adventure.builder()
                .id(entity.getId())
                .ownerId(entity.getCreatorId())
                .name(entity.getName())
                .description(entity.getDescription())
                .maxPlayer(entity.getMaxPlayer())
                .players(new ArrayList<>(entity.getPlayers()))
                .visibility(entity.getVisibility())
                .status(entity.getStatus())
                .postalCode(entity.getPostalCode())
                .city(entity.getCity())
                .invitationCode(entity.getInvitationCode())
                .masterVacantSince(entity.getMasterVacantSince())
                .version(entity.getVersion())
                .build();
    }

    static AdventureEntity fromDomain(Adventure domain) {
        return AdventureEntity.builder()
                .id(domain.getId())
                .creatorId(domain.getOwnerId())
                .name(domain.getName())
                .description(domain.getDescription())
                .maxPlayer(domain.getMaxPlayer())
                .players(new ArrayList<>(domain.getPlayers()))
                .visibility(domain.getVisibility())
                .status(domain.getStatus())
                .postalCode(domain.getPostalCode())
                .city(domain.getCity())
                .invitationCode(domain.getInvitationCode())
                .masterVacantSince(domain.getMasterVacantSince())
                .version(domain.getVersion())
                .build();
    }
}
