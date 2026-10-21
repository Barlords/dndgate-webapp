package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNoteCreationRequest;
import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNoteDto;
import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNotePatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;


public interface AdventureNoteDtoMapper {

    static AdventureNoteDto toDto(AdventureNote domain) {
        return new AdventureNoteDto(
                domain.getId(),
                domain.getContent(),
                domain.getDate(),
                domain.getNoteVisibility()
        );
    }

    static AdventureNote creationRequestToDomain(AdventureNoteCreationRequest request) {
        return AdventureNote.builder()
                .content(request.content().trim())
                .date(request.date())
                .noteVisibility(request.visibility())
                .build();
    }

    static AdventureNote patchRequestToDomain(AdventureNotePatchRequest request) {
        return AdventureNote.builder()
                .content(Text.trimmed(request.content()))
                .date(request.date())
                .noteVisibility(request.visibility())
                .build();
    }

}
