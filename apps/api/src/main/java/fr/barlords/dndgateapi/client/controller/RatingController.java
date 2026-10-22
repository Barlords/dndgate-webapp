package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.PageDto;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.client.dto.rating.RatingCreationRequest;
import fr.barlords.dndgateapi.client.dto.rating.RatingDto;
import fr.barlords.dndgateapi.client.dto.rating.RatingMeanDto;
import fr.barlords.dndgateapi.client.mapper.RatingDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingFinderApi;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingUpdaterApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@Tag(name = "Ratings")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/ratings")
public class RatingController {

    private final AuthVerifierService authVerifierService;

    private final RatingCreatorApi ratingCreatorApi;

    private final RatingUpdaterApi ratingUpdaterApi;

    private final RatingFinderApi ratingFinderApi;

    // GET //

    @GetMapping
    @ResponseStatus(OK)
    public PageDto<RatingDto> getRatingsByAccount(@RequestParam String targetedAccountId,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return PageDto.of(ratingFinderApi.findByTargetedAccountId(authVerifierService.currentAccountId(), UuidValidator.validate(targetedAccountId),
                PageRequest.of(page, size)), RatingDtoMapper::toDto);
    }

    @GetMapping("/between")
    @ResponseStatus(OK)
    public RatingDto getRatingBetween(@RequestParam String emitterId, @RequestParam String targetedAccountId) {
        return ratingFinderApi.findByEmitterIdAndTargetedAccountId(
                        authVerifierService.currentAccountId(),
                        UuidValidator.validate(emitterId),
                        UuidValidator.validate(targetedAccountId))
                .map(RatingDtoMapper::toDto)
                .orElseThrow(RatingNotFoundException::new);
    }

    @GetMapping("/mean")
    @ResponseStatus(OK)
    public RatingMeanDto getMeanRateOfAccount(@RequestParam String accountId) {
        return RatingDtoMapper.toDto(ratingFinderApi.summarizeRatingsOfAccount(UuidValidator.validate(accountId)));
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public RatingDto createRating(@Valid @RequestBody RatingCreationRequest request) {
        return RatingDtoMapper.toDto(
                ratingCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        RatingDtoMapper.creationRequestToDomain(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "200", description = "Updated")})
    @PutMapping
    public ResponseEntity<RatingDto> putRating(@Valid @RequestBody RatingCreationRequest request) {
        Upserted<Rating> result = ratingUpdaterApi.createOrUpdate(
                authVerifierService.currentAccountId(),
                RatingDtoMapper.creationRequestToDomain(request));
        return ResponseEntity.status(result.created() ? CREATED : OK).body(RatingDtoMapper.toDto(result.value()));
    }

}
