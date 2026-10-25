package fr.barlords.dndgateapi.server.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanPrice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads the plan catalog from its JSON file; an unknown key is refused rather than ignored. */
public class PlanCatalogReader {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public PlanCatalog read(InputStream json) {
        JsonNode root = mapper.readTree(json);
        List<Plan> plans = new ArrayList<>();
        for (JsonNode node : root.get("plans")) {
            plans.add(readPlan(node));
        }
        return new PlanCatalog(root.get("currency").asString(), root.get("defaultPlan").asString(), plans);
    }

    private Plan readPlan(JsonNode node) {
        Map<BillingInterval, PlanPrice> prices = new EnumMap<>(BillingInterval.class);
        node.path("prices").properties().forEach(entry -> {
            BillingInterval interval = BillingInterval.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown billing interval: " + entry.getKey()));
            prices.put(interval, new PlanPrice(entry.getValue().get("amount").asInt(),
                    entry.getValue().get("stripeLookupKey").asString()));
        });
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        node.path("limits").properties().forEach(entry -> {
            PlanLimit limit = PlanLimit.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown plan limit: " + entry.getKey()));
            limits.put(limit, entry.getValue().asInt());
        });
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        node.path("features").properties().forEach(entry -> {
            Feature feature = Feature.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown plan feature: " + entry.getKey()));
            features.put(feature, entry.getValue().asBoolean());
        });
        JsonNode badgeNode = node.path("badge");
        PlanBadge badge = badgeNode.isNull() || badgeNode.isMissingNode() ? null
                : PlanBadge.fromKey(badgeNode.asString()).orElseThrow(
                        () -> new IllegalArgumentException("Unknown plan badge: " + badgeNode.asString()));
        return new Plan(node.get("id").asString(), node.path("highlighted").asBoolean(false), prices, limits,
                features, badge);
    }
}
