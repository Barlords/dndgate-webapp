package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.server.entity.RatingEntity;

public interface RatingEntityMapper {

    static Rating toDomain(RatingEntity entity) {
        return Rating.builder()
                .id(entity.getId())
                .emitterId(entity.getEmitterId())
                .accountTargetedId(entity.getTargetId())
                .rate(entity.getRate())
                .build();
    }

    static RatingEntity fromDomain(Rating domain) {
        return RatingEntity.builder()
                .id(domain.getId())
                .emitterId(domain.getEmitterId())
                .targetId(domain.getAccountTargetedId())
                .rate(domain.getRate())
                .build();
    }

}
