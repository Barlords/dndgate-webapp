package fr.barlords.dndgateapi.server.mapper;

import java.util.ArrayList;

import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.server.entity.PlanningEntity;

public interface PlanningEntityMapper {
    static Planning toDomain(PlanningEntity entity) {
        return Planning.builder()
            .id(entity.getId())
            .adventureId(entity.getAdventureId())
            .propositionDates(new ArrayList<>(entity.getPropositionDates()))
            .version(entity.getVersion())
            .build();
    }

    static PlanningEntity fromDomain(Planning domain) {
        return PlanningEntity.builder()
            .id(domain.getId())
            .adventureId(domain.getAdventureId())
            .propositionDates(new ArrayList<>(domain.getPropositionDates()))
            .version(domain.getVersion())
            .build();
    }
}
