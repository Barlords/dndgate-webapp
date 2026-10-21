package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.rating.RatingCreationRequest;
import fr.barlords.dndgateapi.client.dto.rating.RatingDto;
import fr.barlords.dndgateapi.client.dto.rating.RatingPatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.Rating;


public interface RatingDtoMapper {

    static RatingDto toDto(Rating domain) {
        return new RatingDto(
                domain.getId(),
                domain.getEmitterId(),
                domain.getAccountTargetedId(),
                domain.getRate()
        );
    }

    static Rating creationRequestToDomain(RatingCreationRequest request) {
        return Rating.builder()
                .accountTargetedId(request.accountTargetedId())
                .rate(request.rate())
                .build();
    }

    static Rating patchRequestToDomain(RatingPatchRequest request) {
        return Rating.builder()
                .rate(request.rate())
                .build();
    }

}
