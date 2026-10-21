package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.PageDto;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNoteCreationRequest;
import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNoteDto;
import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNotePatchRequest;
import fr.barlords.dndgateapi.client.dto.ExplicitDeletionRequest;
import fr.barlords.dndgateapi.client.mapper.HistoricalNoteDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.HistoricalNoteNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteUpdaterApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Historical notes")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/historical-notes")
public class HistoricalNoteController {

    private final AuthVerifierService authVerifierService;

    private final HistoricalNoteCreatorApi historicalNoteCreatorApi;

    private final HistoricalNoteFinderApi historicalNoteFinderApi;

    private final HistoricalNoteUpdaterApi historicalNoteUpdaterApi;

    private final HistoricalNoteDeleterApi historicalNoteDeleterApi;

    // GET //

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public HistoricalNoteDto getHistoricalNoteById(@PathVariable String id) {
        return historicalNoteFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(HistoricalNoteDtoMapper::toDto)
                .orElseThrow(HistoricalNoteNotFoundException::new);
    }

    @GetMapping
    @ResponseStatus(OK)
    public PageDto<HistoricalNoteDto> getHistoricalNotesByAdventureId(@RequestParam String adventureId,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return PageDto.of(historicalNoteFinderApi.findByAdventureId(authVerifierService.currentAccountId(), UuidValidator.validate(adventureId),
                PageRequest.of(page, size)), HistoricalNoteDtoMapper::toDto);
    }


    // POST //

    @PostMapping
    @ResponseStatus(CREATED)
    public HistoricalNoteDto createHistoricalNote(
            @Valid @RequestBody HistoricalNoteCreationRequest request) {
        return HistoricalNoteDtoMapper.toDto(
                historicalNoteCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        request.adventureId(),
                        HistoricalNoteDtoMapper.creationRequestToDomain(request)
                )
        );
    }


    // PATCH //

    @PatchMapping("/{id}")
    @ResponseStatus(CREATED)
    public HistoricalNoteDto patchHistoricalNote(
            @PathVariable String id,
            @Valid @RequestBody HistoricalNotePatchRequest request) {
        return HistoricalNoteDtoMapper.toDto(
                historicalNoteUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        request.adventureId(),
                        HistoricalNoteDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }


    // DELETE //

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteHistoricalNote(@PathVariable String id, @Valid @RequestBody ExplicitDeletionRequest request) {
        historicalNoteDeleterApi.deleteById(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                request.confirm()
        );
    }

}
