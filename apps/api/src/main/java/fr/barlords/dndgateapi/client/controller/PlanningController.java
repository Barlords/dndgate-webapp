package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.planning.AvailabilityCreationRequest;
import fr.barlords.dndgateapi.client.dto.planning.AvailabilityDto;
import fr.barlords.dndgateapi.client.dto.planning.PlanningCreationRequest;
import fr.barlords.dndgateapi.client.dto.planning.PlanningDto;
import fr.barlords.dndgateapi.client.mapper.AvailabilityDtoMapper;
import fr.barlords.dndgateapi.client.mapper.PlanningDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.in.planning.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@Tag(name = "Planning")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/planning")
public class PlanningController {

    private final AuthVerifierService authVerifierService;
    private final PlanningCreatorApi planningCreatorApi;
    private final AvailabilityCreatorApi availabilityCreatorApi;
    private final PlanningFinderApi planningFinderApi;
    private final AvailabilityFinderApi availabilityFinderApi;
    private final PlanningUpdaterApi planningUpdaterApi;
    private final AvailabilityUpdaterApi availabilityUpdaterApi;



    //MJ

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanningDto createPlanning(@Valid @RequestBody PlanningCreationRequest request) {
        return PlanningDtoMapper.toDto(
                planningCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        PlanningDtoMapper.creationRequestToDomain(request)
                )
        );
    }

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public PlanningDto getPlanningById(@PathVariable String id) {
        return planningFinderApi.findById(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(PlanningDtoMapper::toDto)
                .orElseThrow(PlanningNotFoundException::new);
    }

    @GetMapping(params = "adventureId")
    @ResponseStatus(OK)
    public PlanningDto getPlanningByAdventureId(@RequestParam String adventureId) {
        return planningFinderApi.findByAdventureId(authVerifierService.currentAccountId(), UuidValidator.validate(adventureId))
                .map(PlanningDtoMapper::toDto)
                .orElseThrow(PlanningNotFoundException::new);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "200", description = "Updated")})
    @PutMapping
    public ResponseEntity<PlanningDto> putPlanning(@Valid @RequestBody PlanningCreationRequest request) {
        Upserted<Planning> result = planningUpdaterApi.createOrUpdate(
                authVerifierService.currentAccountId(),
                PlanningDtoMapper.creationRequestToDomain(request));
        return ResponseEntity.status(result.created() ? CREATED : OK).body(PlanningDtoMapper.toDto(result.value()));
    }


    //Joueur

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityDto createAvailability(@Valid @RequestBody AvailabilityCreationRequest request) {
        return AvailabilityDtoMapper.toDto(
                availabilityCreatorApi.create(
                        authVerifierService.currentAccountId(),
                        AvailabilityDtoMapper.creationRequestToDomain(request)
                )
        );
    }

    @GetMapping("/{planningId}/availability")
    @ResponseStatus(HttpStatus.OK)
    public List<AvailabilityDto> getAvailabilitiesByPlanningId(@PathVariable UUID planningId) {
        List<Availability> availabilities = availabilityFinderApi.findByPlanningId(authVerifierService.currentAccountId(), planningId);
        return availabilities.stream()
                .map(AvailabilityDtoMapper::toDto)
                .collect(Collectors.toList());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "200", description = "Updated")})
    @PutMapping("/availability")
    public ResponseEntity<AvailabilityDto> putAvailability(@Valid @RequestBody AvailabilityCreationRequest request) {
        Upserted<Availability> result = availabilityUpdaterApi.createOrUpdate(
                authVerifierService.currentAccountId(),
                AvailabilityDtoMapper.creationRequestToDomain(request));
        return ResponseEntity.status(result.created() ? CREATED : OK).body(AvailabilityDtoMapper.toDto(result.value()));
    }

}
