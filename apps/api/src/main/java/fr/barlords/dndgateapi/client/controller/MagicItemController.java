package fr.barlords.dndgateapi.client.controller;

import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemCreationRequest;
import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemDto;
import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemPatchRequest;
import fr.barlords.dndgateapi.client.mapper.MagicItemDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemFinderApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemUpdaterApi;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Magic items")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/magic-items")
public class MagicItemController {

    private final AuthVerifierService authVerifierService;

    private final MagicItemCreatorApi magicItemCreatorApi;

    private final MagicItemFinderApi magicItemFinderApi;

    private final MagicItemUpdaterApi magicItemUpdaterApi;

    private final MagicItemDeleterApi magicItemDeleterApi;

    // GET //

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public MagicItemDto getMagicItemById(@PathVariable String id) {
        return magicItemFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(MagicItemDtoMapper::toDto)
                .orElseThrow(MagicItemNotFoundException::new);
    }

    @GetMapping("/mine")
    @ResponseStatus(OK)
    public List<MagicItemDto> getMyMagicItems() {
        return magicItemFinderApi.findMine(authVerifierService.currentAccountId())
                .stream()
                .map(MagicItemDtoMapper::toDto)
                .toList();
    }

    // POST //

    @PostMapping
    @ResponseStatus(CREATED)
    public MagicItemDto createMagicItem(@Valid @RequestBody MagicItemCreationRequest request) {
        return MagicItemDtoMapper.toDto(
                magicItemCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        MagicItemDtoMapper.creationRequestToDomain(request)
                )
        );
    }

    // PATCH //

    @PatchMapping("/{id}")
    @ResponseStatus(OK)
    public MagicItemDto patchMagicItem(
            @PathVariable String id,
            @Valid @RequestBody MagicItemPatchRequest request) {
        return MagicItemDtoMapper.toDto(
                magicItemUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        MagicItemDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }

    // DELETE //

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteMagicItemById(@PathVariable String id, @Valid @RequestBody DeletionConfirmationRequest request) {
        magicItemDeleterApi.deleteById(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                request.confirmation()
        );
    }

}
