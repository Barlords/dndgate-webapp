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

/**
 * Reads the plan catalog from its JSON file; an unknown key is refused rather than ignored. The types are strict
 * (no "1" for 1, no 1.5 for a quantity, no 1 for true) and every refusal names the key at fault.
 */
public class PlanCatalogReader {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public PlanCatalog read(InputStream json) {
        JsonNode root = mapper.readTree(json);
        JsonNode planNodes = required(root, "plans");
        if (!planNodes.isArray()) {
            throw new IllegalArgumentException("The plan catalog key plans must be an array");
        }
        List<Plan> plans = new ArrayList<>();
        for (JsonNode node : planNodes) {
            plans.add(readPlan(node));
        }
        return new PlanCatalog(string(root, "currency"), string(root, "defaultPlan"), plans);
    }

    private Plan readPlan(JsonNode node) {
        Map<BillingInterval, PlanPrice> prices = new EnumMap<>(BillingInterval.class);
        node.path("prices").properties().forEach(entry -> {
            BillingInterval interval = BillingInterval.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown billing interval: " + entry.getKey()));
            prices.put(interval, new PlanPrice(integer(required(entry.getValue(), "amount"), "amount"),
                    string(entry.getValue(), "stripeLookupKey")));
        });
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        node.path("limits").properties().forEach(entry -> {
            PlanLimit limit = PlanLimit.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown plan limit: " + entry.getKey()));
            limits.put(limit, integer(entry.getValue(), entry.getKey()));
        });
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        node.path("features").properties().forEach(entry -> {
            Feature feature = Feature.fromKey(entry.getKey()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown plan feature: " + entry.getKey()));
            features.put(feature, bool(entry.getValue(), entry.getKey()));
        });
        JsonNode badgeNode = node.path("badge");
        PlanBadge badge = badgeNode.isNull() || badgeNode.isMissingNode() ? null
                : PlanBadge.fromKey(string(node, "badge")).orElseThrow(
                        () -> new IllegalArgumentException("Unknown plan badge: " + badgeNode.asString()));
        JsonNode highlighted = node.path("highlighted");
        return new Plan(string(node, "id"), !highlighted.isMissingNode() && bool(highlighted, "highlighted"), prices,
                limits, features, badge);
    }

    private static JsonNode required(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("The plan catalog key " + key + " is missing");
        }
        return value;
    }

    private static String string(JsonNode node, String key) {
        JsonNode value = required(node, key);
        if (!value.isString()) {
            throw new IllegalArgumentException("The plan catalog key " + key + " must be a string: " + value);
        }
        return value.asString();
    }

    private static int integer(JsonNode value, String key) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("The plan catalog key " + key + " must be a whole number: " + value);
        }
        return value.intValue();
    }

    private static boolean bool(JsonNode value, String key) {
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("The plan catalog key " + key + " must be true or false: " + value);
        }
        return value.booleanValue();
    }
}
