package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Arrays;
import java.util.Optional;

public enum PlanBadge {
    DUNGEON_MASTER("dungeon-master"), LEGEND("legend");

    private final String key;

    PlanBadge(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<PlanBadge> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
