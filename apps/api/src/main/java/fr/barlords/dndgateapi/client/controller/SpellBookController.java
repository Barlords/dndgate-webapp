package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import fr.barlords.dndgateapi.client.dto.spellbook.*;
import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.mapper.SpellBookDtoMapper;
import fr.barlords.dndgateapi.client.mapper.SpellBookImportExportDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookExportException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookImportException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.in.spellbook.*;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellWriterApi;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Spell books")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/spellbooks")
public class SpellBookController {

    private final AuthVerifierService authVerifierService;

    private final SpellBookCreatorApi spellBookCreatorApi;

    private final SpellBookFinderApi spellBookFinderApi;

    private final SpellBookUpdaterApi spellBookUpdaterApi;

    private final SpellBookDeleterApi spellBookDeleterApi;

    private final SpellBookImporterApi spellBookImporterApi;

    private final SpellWriterApi spellWriterApi;

    private final SpellDeleterApi spellDeleterApi;

    private final Validator validator;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // GET //


    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public SpellBookDto getSpellBookById(@PathVariable String id) {
        return spellBookFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(SpellBookDtoMapper::toDto)
                .orElseThrow(SpellBookNotFoundException::new);
    }

    @GetMapping(params = "ownerId")
    @ResponseStatus(OK)
    public List<SpellBookDto> getSpellBooksByOwnerId(@RequestParam String ownerId) {
        return spellBookFinderApi.findByOwnerId(authVerifierService.currentAccountId(), UuidValidator.validate(ownerId))
                .stream()
                .map(SpellBookDtoMapper::toDto)
                .toList();
    }

    @GetMapping("/mine")
    @ResponseStatus(OK)
    public List<SpellBookDto> getMySpellBooks() {
        return spellBookFinderApi.findMyBook(authVerifierService.currentAccountId())
                .stream()
                .map(SpellBookDtoMapper::toDto)
                .toList();
    }

    @GetMapping("/{id}/export-file")
    @ResponseStatus(OK)
    public ResponseEntity<byte[]> exportSpellBookFile(@PathVariable String id) {
        SpellBook spellBook = spellBookFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .orElseThrow(SpellBookNotFoundException::new);

        SpellBookImportExportDto dto = SpellBookImportExportDtoMapper.toDto(spellBook);
        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writeValueAsBytes(dto);
        }
        catch (JacksonException e) {
            throw new SpellBookExportException();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment().filename("book.json").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(jsonBytes);
    }

    // POST //

    @PostMapping
    @ResponseStatus(CREATED)
    public SpellBookDto createSpellBook(@Valid @RequestBody SpellBookCreationRequest request) {
        return SpellBookDtoMapper.toDto(
                spellBookCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        SpellBookDtoMapper.creationRequestToDomain(request)
                )
        );
    }

    @PostMapping("/import-file")
    @ResponseStatus(CREATED)
    public SpellBookDto importSpellBookFile(@RequestParam("file") MultipartFile file) {
        SpellBookImportExportDto spellBookImportDto;
        try {
            spellBookImportDto = objectMapper.readValue(file.getInputStream(), SpellBookImportExportDto.class);
        } catch (IOException | JacksonException e) {
            throw new SpellBookImportException();
        }

        Set<ConstraintViolation<SpellBookImportExportDto>> violations = validator.validate(spellBookImportDto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return SpellBookDtoMapper.toDto(
                spellBookImporterApi.importSpellBook(
                        authVerifierService.currentAccountId(),
                        SpellBookImportExportDtoMapper.importToDomain(spellBookImportDto)
                )
        );
    }

    @PostMapping("/{id}/spells")
    @ResponseStatus(CREATED)
    public SpellBookDto addSpellToBook(
            @PathVariable String id,
            @Valid @RequestBody AddSpellToSpellBookRequest request) {
        return SpellBookDtoMapper.toDto(
                spellWriterApi.addSpell(
                        authVerifierService.currentAccountId(),
                        UuidValidator.validate(id),
                        request.spellIndex()
                )
        );
    }

    // PATCH //

    @PatchMapping("/{id}")
    @ResponseStatus(CREATED)
    public SpellBookDto patchSpellBook(
            @PathVariable String id,
            @Valid @RequestBody SpellBookPatchRequest request) {
        return SpellBookDtoMapper.toDto(
                spellBookUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        SpellBookDtoMapper.patchRequestToDomain(request).withId(UuidValidator.validate(id))
                )
        );
    }

    // DELETE //

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteSpellBookById(@PathVariable String id, @Valid @RequestBody DeletionConfirmationRequest request) {
        spellBookDeleterApi.deleteById(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                request.confirmation()
        );
    }

    @DeleteMapping("/{id}/spells/{spellIndex}")
    @ResponseStatus(NO_CONTENT)
    public void removeSpellFromBook(@PathVariable String id, @PathVariable String spellIndex) {
        spellDeleterApi.deleteSpell(
                authVerifierService.currentAccountId(),
                UuidValidator.validate(id),
                spellIndex
        );
    }

}
