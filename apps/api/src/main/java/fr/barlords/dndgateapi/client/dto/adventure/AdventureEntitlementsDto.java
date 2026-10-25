package fr.barlords.dndgateapi.client.dto.adventure;

import fr.barlords.dndgateapi.client.dto.plan.LimitUsageDto;

import java.util.List;
import java.util.Map;

/** Features by their JSON key and adventure limits by their JSON key. */
public record AdventureEntitlementsDto(List<String> features, Map<String, LimitUsageDto> limits) {
}
