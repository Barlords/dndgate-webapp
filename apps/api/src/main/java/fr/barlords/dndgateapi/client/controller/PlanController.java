package fr.barlords.dndgateapi.client.controller;

import fr.barlords.dndgateapi.client.dto.plan.PlanCatalogDto;
import fr.barlords.dndgateapi.client.mapper.PlanDtoMapper;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.OK;

@Tag(name = "Plans")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/plans")
public class PlanController {

    private final PlanCatalog catalog;

    @GetMapping
    @ResponseStatus(OK)
    @Operation(summary = "Get the plans", description = "The offers, their prices in cents, limits and features. Public.")
    public PlanCatalogDto getPlans() {
        return PlanDtoMapper.toDto(catalog);
    }
}
