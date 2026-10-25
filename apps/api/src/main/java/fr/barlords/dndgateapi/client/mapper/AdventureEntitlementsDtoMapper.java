package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.adventure.AdventureEntitlementsDto;
import fr.barlords.dndgateapi.client.dto.plan.LimitUsageDto;
import fr.barlords.dndgateapi.domain.functional.model.plan.AdventureEntitlements;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;

import java.util.LinkedHashMap;
import java.util.Map;

public interface AdventureEntitlementsDtoMapper {

    static AdventureEntitlementsDto toDto(AdventureEntitlements domain) {
        Map<String, LimitUsageDto> limits = new LinkedHashMap<>();
        domain.limits().forEach((limit, usage) -> limits.put(limit.key(), new LimitUsageDto(usage.used(), usage.max())));
        return new AdventureEntitlementsDto(domain.features().stream().map(Feature::key).sorted().toList(), limits);
    }
}
