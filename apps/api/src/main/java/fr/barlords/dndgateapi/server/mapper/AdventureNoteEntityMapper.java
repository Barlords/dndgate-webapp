package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.server.entity.AdventureNoteEntity;

public interface AdventureNoteEntityMapper {

    static AdventureNote toDomain(AdventureNoteEntity entity) {
        return AdventureNote.builder()
                .id(entity.getId())
                .adventure(AdventureEntityMapper.toDomain(entity.getAdventure()))
                .content(entity.getContent())
                .date(entity.getDate())
                .noteVisibility(entity.getNoteVisibility())
                .build();
    }

    static AdventureNoteEntity fromDomain(AdventureNote domain) {
        return AdventureNoteEntity.builder()
                .id(domain.getId())
                .adventure(AdventureEntityMapper.fromDomain(domain.getAdventure()))
                .content(domain.getContent())
                .date(domain.getDate())
                .noteVisibility(domain.getNoteVisibility())
                .build();
    }

}
