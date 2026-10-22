package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.adventure.AdventureCreationRequest;
import fr.barlords.dndgateapi.client.dto.adventure.AdventureDto;
import fr.barlords.dndgateapi.client.dto.adventure.AdventurePatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.ArrayList;

public interface AdventureDtoMapper {
    static AdventureDto toDto(Adventure domain) {
        return new AdventureDto(
                domain.getId(),
                domain.getOwnerId(),
                domain.getName(),
                domain.getDescription(),
                domain.getMaxPlayer(),
                domain.getPlayers(),
                domain.getStatus(),
                domain.getVisibility(),
                domain.getPostalCode(),
                domain.getCity(),
                domain.isMasterVacant()
        );
    }

    static Adventure creationRequestToDomain(AdventureCreationRequest request) {
        return Adventure.builder()
                .name(request.name().trim())
                .description(request.description().trim())
                .maxPlayer(request.maxPlayer())
                .players(new ArrayList<>())
                .visibility(request.visibility())
                .postalCode(request.postalCode().trim())
                .city(request.city().trim())
                .build();
    }

    static Adventure patchRequestToDomain(AdventurePatchRequest request) {
        return Adventure.builder()
                .name(request.name())
                .description(request.description())
                .maxPlayer(request.maxPlayer())
                .visibility(request.visibility())
                .postalCode(request.postalCode())
                .city(request.city())
                .build();
    }
}