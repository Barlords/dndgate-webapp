package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.server.entity.AvailabilityEntity;

import java.util.ArrayList;

public interface AvailabilityEntityMapper {

     static Availability toDomain(AvailabilityEntity entity) {
        return Availability.builder()
                .id(entity.getId())
                .planningId(entity.getPlanningId())
                .accountId(entity.getAccountId())
                .availabilityDates(new ArrayList<>(entity.getAvailabilityDates()))
                .build();
    }

     static AvailabilityEntity fromDomain(Availability domain) {
        AvailabilityEntity entity = new AvailabilityEntity();
        entity.setId(domain.getId());
        entity.setPlanningId(domain.getPlanningId());
        entity.setAccountId(domain.getAccountId());
        entity.setAvailabilityDates(new ArrayList<>(domain.getAvailabilityDates()));
        return entity;
    }
}

