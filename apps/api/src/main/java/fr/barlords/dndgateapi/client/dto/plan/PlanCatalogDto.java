package fr.barlords.dndgateapi.client.dto.plan;

import java.util.List;

/** The offers, from the cheapest to the most complete; {@code defaultPlan} is the one of an account without subscription. */
public record PlanCatalogDto(String currency, String defaultPlan, List<PlanDto> plans) {
}
