package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNoteCreationRequest;
import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNoteDto;
import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNotePatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;


public interface HistoricalNoteDtoMapper {

    static HistoricalNoteDto toDto(HistoricalNote domain) {
        return new HistoricalNoteDto(
                domain.getId(),
                domain.getContent(),
                domain.getDate()
        );
    }

    static HistoricalNote creationRequestToDomain(HistoricalNoteCreationRequest request) {
        return HistoricalNote.builder()
                .content(request.content().trim())
                .date(request.date())
                .build();
    }

    static HistoricalNote patchRequestToDomain(HistoricalNotePatchRequest request) {
        return HistoricalNote.builder()
                .content(Text.trimmed(request.content()))
                .date(request.date())
                .build();
    }

}
