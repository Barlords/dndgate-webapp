package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.rating.RatingCreationRequest;
import fr.barlords.dndgateapi.client.dto.rating.RatingDto;
import fr.barlords.dndgateapi.client.dto.rating.RatingMeanDto;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.RatingSummary;


public interface RatingDtoMapper {

    static RatingDto toDto(Rating domain) {
        return new RatingDto(
                domain.getId(),
                domain.getEmitterId(),
                domain.getAccountTargetedId(),
                domain.getRate()
        );
    }

    static RatingMeanDto toDto(RatingSummary summary) {
        return new RatingMeanDto(summary.average(), summary.count());
    }

    static Rating creationRequestToDomain(RatingCreationRequest request) {
        return Rating.builder()
                .accountTargetedId(request.accountTargetedId())
                .rate(request.rate())
                .build();
    }

}
