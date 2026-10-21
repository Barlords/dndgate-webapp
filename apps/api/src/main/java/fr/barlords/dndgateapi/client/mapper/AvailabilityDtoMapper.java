package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.planning.AvailabilityCreationRequest;
import fr.barlords.dndgateapi.client.dto.planning.AvailabilityDto;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;


public interface AvailabilityDtoMapper {

    static AvailabilityDto toDto(Availability domain) {
        return new AvailabilityDto(
                domain.getId(),
                domain.getPlanningId(),
                domain.getAccountId(),
                domain.getAvailabilityDates()
        );
    }

    static Availability creationRequestToDomain(AvailabilityCreationRequest request) {
        return Availability.builder()
                .planningId(request.planningId())
                .availabilityDates(request.propositionDates())
                .build();
    }
}
