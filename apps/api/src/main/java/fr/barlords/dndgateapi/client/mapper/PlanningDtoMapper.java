package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.planning.PlanningCreationRequest;
import fr.barlords.dndgateapi.client.dto.planning.PlanningDto;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;


public interface PlanningDtoMapper {

    static PlanningDto toDto(Planning domain) {
        return new PlanningDto(
                domain.getId(),
                domain.getAdventureId(),
                domain.getPropositionDates()
        );
    }

    static  Planning creationRequestToDomain(PlanningCreationRequest request) {
        return Planning.builder()
                .adventureId(request.adventureId())
                .propositionDates(request.propositionDates())
                .build();
    }
}
