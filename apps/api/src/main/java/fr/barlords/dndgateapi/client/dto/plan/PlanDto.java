package fr.barlords.dndgateapi.client.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * An offer of the catalog.
 *
 * @param prices   by billing interval ({@code monthly}, {@code yearly}); empty for the free plan
 * @param limits   by the JSON key of the limit ({@code spellBooks}, ...)
 * @param features by the JSON key of the feature ({@code video}, ...)
 * @param badge    {@code dungeon-master}, {@code legend} or null
 */
public record PlanDto(
        String id,
        boolean highlighted,
        Map<String, PlanPriceDto> prices,
        Map<String, Integer> limits,
        Map<String, Boolean> features,
        @Schema(nullable = true, description = "dungeon-master, legend or null") String badge) {
}
