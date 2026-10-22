package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacterPicture;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.time.Duration;

import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterCreationRequest;
import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterDto;
import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterPatchRequest;
import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.mapper.GameCharacterDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterFinderApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterUpdaterApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Game characters")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/game-characters")
public class GameCharacterController {

    private final AuthVerifierService authVerifierService;

    private final GameCharacterCreatorApi gameCharacterCreatorApi;

    private final GameCharacterFinderApi gameCharacterFinderApi;

    private final GameCharacterUpdaterApi gameCharacterUpdaterApi;

    private final GameCharacterDeleterApi gameCharacterDeleterApi;

    // GET //

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public GameCharacterDto getGameCharacterById(@PathVariable String id) {
        return gameCharacterFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(GameCharacterDtoMapper::toDto)
                .orElseThrow(GameCharacterNotFoundException::new);
    }

    @GetMapping("/{id}/picture")
    public ResponseEntity<byte[]> getGameCharacterPicture(@PathVariable String id) {
        GameCharacterPicture picture = gameCharacterFinderApi.findPicture(authVerifierService.currentAccountId(), UuidValidator.validate(id));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(picture.content());
    }

    @GetMapping(params = "ownerId")
    @ResponseStatus(OK)
    public List<GameCharacterDto> getGameCharactersByOwnerId(@RequestParam String ownerId) {
        return gameCharacterFinderApi.findByOwnerId(authVerifierService.currentAccountId(), UuidValidator.validate(ownerId))
                .stream()
                .map(GameCharacterDtoMapper::toDto)
                .toList();
    }

    @GetMapping(params = "adventureId")
    @ResponseStatus(OK)
    public List<GameCharacterDto> getGameCharactersByAdventureId(@RequestParam String adventureId) {
        return gameCharacterFinderApi.findByAdventureId(authVerifierService.currentAccountId(), UuidValidator.validate(adventureId))
                .stream()
                .map(GameCharacterDtoMapper::toDto)
                .toList();
    }

    @GetMapping("/mine")
    @ResponseStatus(OK)
    public List<GameCharacterDto> getMyGameCharacters() {
        return gameCharacterFinderApi.findMyCharacter(authVerifierService.currentAccountId())
                .stream()
                .map(GameCharacterDtoMapper::toDto)
                .toList();
    }


    // POST //

    @PostMapping(value = "", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @ResponseStatus(CREATED)
    public GameCharacterDto createGameCharacter(
            @Valid @RequestPart("picturePresentation") MultipartFile pictureFile,
            @Valid @RequestPart("createGameCharacterRequest") GameCharacterCreationRequest request) throws IOException {
        return GameCharacterDtoMapper.toDto(
                gameCharacterCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        request.adventureId(),
                        GameCharacterDtoMapper.creationRequestToDomain(request),
                        pictureFile.getInputStream()
                )
        );
    }


    // PATCH //

    @PatchMapping("/{id}")
    @ResponseStatus(OK)
    public GameCharacterDto patchGameCharacter(
            @PathVariable String id,
            @Valid @RequestBody GameCharacterPatchRequest request) {
        return GameCharacterDtoMapper.toDto(
                gameCharacterUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        GameCharacterDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }


    // DELETE //

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteGameCharacterById(@PathVariable String id, @Valid @RequestBody DeletionConfirmationRequest request) {
        gameCharacterDeleterApi.deleteById(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                request.confirmation()
        );
    }

}
