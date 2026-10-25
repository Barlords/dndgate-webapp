package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.PageDto;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.dto.adventure.*;
import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterDto;
import fr.barlords.dndgateapi.client.mapper.GameCharacterDtoMapper;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterMoverApi;
import fr.barlords.dndgateapi.client.mapper.AdventureDtoMapper;
import fr.barlords.dndgateapi.client.mapper.AdventureEntitlementsDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import fr.barlords.dndgateapi.domain.port.in.plan.AdventureEntitlementsApi;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


import static org.springframework.http.HttpStatus.*;

@Tag(name = "Adventures")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/adventures")
public class AdventureController {

    private final AuthVerifierService authVerifierService;
    private final AdventureCreatorApi adventureCreatorApi;
    private final AdventureFinderApi adventureFinderApi;
    private final AdventureDeleterApi adventureDeleterApi;
    private final AdventureUpdaterApi adventureUpdaterApi;
    private final AdventureEntitlementsApi adventureEntitlementsApi;
    private final GameCharacterMoverApi gameCharacterMoverApi;

    @GetMapping
    @ResponseStatus(OK)
    public PageDto<AdventureDto> getAdventures(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return PageDto.of(adventureFinderApi.findVisibleTo(authVerifierService.currentAccountId(), PageRequest.of(page, size)),
                AdventureDtoMapper::toDto);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public AdventureDto createAdventure(@Valid @RequestBody AdventureCreationRequest request) {
        return AdventureDtoMapper.toDto(
                adventureCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        AdventureDtoMapper.creationRequestToDomain(request)
                )
        );
    }
    @PatchMapping("/{id}")
    @ResponseStatus(OK)
    public AdventureDto updateAdventure(@PathVariable String id,
                                       @Valid @RequestBody AdventurePatchRequest request) {
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        AdventureDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public AdventureDto getAdventureById(@PathVariable String id) {
        return adventureFinderApi.findByIdVisibleTo(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(AdventureDtoMapper::toDto)
                .orElseThrow(AdventureNotFoundException::new);
    }

    @GetMapping("/{id}/entitlements")
    @ResponseStatus(OK)
    public AdventureEntitlementsDto getAdventureEntitlements(@PathVariable String id) {
        return AdventureEntitlementsDtoMapper.toDto(
                adventureEntitlementsApi.of(authVerifierService.currentAccountId(), UuidValidator.validate(id)));
    }

    @GetMapping("/as-player")
    @ResponseStatus(HttpStatus.OK)
    public PageDto<AdventureDto> getAdventuresAsPlayer(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return PageDto.of(adventureFinderApi.findWhereImPlayer(authVerifierService.currentAccountId(), PageRequest.of(page, size)),
                AdventureDtoMapper::toDto);
    }

    @GetMapping("/as-master")
    @ResponseStatus(HttpStatus.OK)
    public PageDto<AdventureDto> getAdventuresAsMaster(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return PageDto.of(adventureFinderApi.findWhereImMaster(authVerifierService.currentAccountId(), PageRequest.of(page, size)),
                AdventureDtoMapper::toDto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteAdventureById(@PathVariable String id, @Valid @RequestBody DeletionConfirmationRequest request) {
        adventureDeleterApi.deleteById(authVerifierService.currentAccountId(), UuidValidator.validate(id), request.confirmation());
    }

    @PutMapping("/{id}/visibility")
    @ResponseStatus(OK)
    public AdventureDto updateAdventureVisibility(
            @PathVariable String id,
            @Valid @RequestBody AdventureSetVisibilityRequest request
    ) {
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.updateVisibility(
                        authVerifierService.currentAccountId(),
                        UuidValidator.validate(id),
                        request.visibility()
                )
        );
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    public AdventureDto updateAdventureStatus(
            @PathVariable String id,
            @Valid @RequestBody AdventureSetStatusRequest request
    ) {
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.updateStatus(
                        authVerifierService.currentAccountId(),
                        UuidValidator.validate(id),
                        request.status()
                )
        );
    }

    @PostMapping("/{id}/invitations/{pseudo}")
    @ResponseStatus(ACCEPTED)
    public void invitePlayer(
            @PathVariable String id,
            @PathVariable String pseudo
    ) {
        adventureUpdaterApi.sendInvitationCode(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                pseudo
        );
    }

    @PostMapping("/invitations/accept")
    @ResponseStatus(OK)
    public AdventureDto acceptPlayerInvitation(@Valid @RequestBody JoinAdventureByCodeRequest request) {
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.joinAdventureByCode(
                        authVerifierService.currentAccountId(),
                        request.code()
                )
        );
    }

    @PostMapping("/{id}/join")
    @ResponseStatus(OK)
    public AdventureDto joinAdventure(@PathVariable String id) {
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.joinAdventure(
                        UuidValidator.validate(id),
                        authVerifierService.currentAccountId()
                )
        );
    }

    @DeleteMapping("/{id}/players/me")
    @ResponseStatus(OK)
    public AdventureDto quitAdventure(@PathVariable String id, @Valid @RequestBody(required = false) QuitAdventureRequest request) {
        Map<UUID, String> deletions = new LinkedHashMap<>();
        if (request != null && request.deletions() != null) {
            for (CharacterDeletion deletion : request.deletions()) {
                // The same character twice would be a contradictory request
                if (deletions.put(deletion.characterId(), deletion.confirmation()) != null) {
                    throw new DeletionNotConfirmedException();
                }
            }
        }
        return AdventureDtoMapper.toDto(
                adventureUpdaterApi.quitAdventure(
                        UuidValidator.validate(id),
                        authVerifierService.currentAccountId(),
                        deletions
                )
        );
    }

    @PutMapping("/{id}/players/me/active-character")
    @ResponseStatus(OK)
    public GameCharacterDto setActiveCharacter(@PathVariable String id, @Valid @RequestBody ActiveCharacterRequest request) {
        return GameCharacterDtoMapper.toDto(
                gameCharacterMoverApi.activate(
                        authVerifierService.currentAccountId(),
                        UuidValidator.validate(id),
                        request.characterId()
                )
        );
    }

    @GetMapping("/available")
    @ResponseStatus(OK)
    public PageDto<AdventureDto> getAvailableAdventures(@RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
        return PageDto.of(adventureFinderApi.findAvailableAdventures(authVerifierService.currentAccountId(), PageRequest.of(page, size)),
                AdventureDtoMapper::toDto);
    }

}


