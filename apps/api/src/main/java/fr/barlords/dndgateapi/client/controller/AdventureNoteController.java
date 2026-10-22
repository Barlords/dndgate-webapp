package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.PageDto;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNoteCreationRequest;
import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNoteDto;
import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNotePatchRequest;
import fr.barlords.dndgateapi.client.dto.ExplicitDeletionRequest;
import fr.barlords.dndgateapi.client.mapper.AdventureNoteDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.AdventureNoteNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteUpdaterApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Adventure notes")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/adventure-notes")
public class AdventureNoteController {

    private final AuthVerifierService authVerifierService;

    private final AdventureNoteCreatorApi adventureNoteCreatorApi;

    private final AdventureNoteFinderApi adventureNoteFinderApi;

    private final AdventureNoteUpdaterApi adventureNoteUpdaterApi;

    private final AdventureNoteDeleterApi adventureNoteDeleterApi;

    // GET //

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public AdventureNoteDto getAdventureNoteById(@PathVariable String id) {
        return adventureNoteFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(AdventureNoteDtoMapper::toDto)
                .orElseThrow(AdventureNoteNotFoundException::new);
    }

    @GetMapping
    @ResponseStatus(OK)
    public PageDto<AdventureNoteDto> getAdventureNotesByAdventureId(@RequestParam String adventureId,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return PageDto.of(adventureNoteFinderApi.findByAdventureId(authVerifierService.currentAccountId(), UuidValidator.validate(adventureId),
                PageRequest.of(page, size)), AdventureNoteDtoMapper::toDto);
    }


    // POST //

    @PostMapping
    @ResponseStatus(CREATED)
    public AdventureNoteDto createAdventureNote(
            @Valid @RequestBody AdventureNoteCreationRequest request) {
        return AdventureNoteDtoMapper.toDto(
                adventureNoteCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        request.adventureId(),
                        AdventureNoteDtoMapper.creationRequestToDomain(request)
                )
        );
    }


    // PATCH //

    @PatchMapping("/{id}")
    @ResponseStatus(OK)
    public AdventureNoteDto patchAdventureNote(
            @PathVariable String id,
            @Valid @RequestBody AdventureNotePatchRequest request) {
        return AdventureNoteDtoMapper.toDto(
                adventureNoteUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        AdventureNoteDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }


    // DELETE //

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteAdventureNote(@PathVariable String id, @Valid @RequestBody ExplicitDeletionRequest request) {
        adventureNoteDeleterApi.deleteById(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                request.confirm()
        );
    }

}
