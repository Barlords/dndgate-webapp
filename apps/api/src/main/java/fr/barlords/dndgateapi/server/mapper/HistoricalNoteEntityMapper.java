package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.server.entity.HistoricalNoteEntity;

public interface HistoricalNoteEntityMapper {

    static HistoricalNote toDomain(HistoricalNoteEntity entity) {
        return HistoricalNote.builder()
                .id(entity.getId())
                .adventure(AdventureEntityMapper.toDomain(entity.getAdventure()))
                .content(entity.getContent())
                .date(entity.getDate())
                .build();
    }

    static HistoricalNoteEntity fromDomain(HistoricalNote domain) {
        return HistoricalNoteEntity.builder()
                .id(domain.getId())
                .adventure(AdventureEntityMapper.fromDomain(domain.getAdventure()))
                .content(domain.getContent())
                .date(domain.getDate())
                .build();
    }

}
