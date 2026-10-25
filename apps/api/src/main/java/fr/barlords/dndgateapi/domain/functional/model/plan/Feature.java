package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Arrays;
import java.util.Optional;

/** A capability that a plan grants or not; its reach says who benefits from it. */
public enum Feature {
    CHARACTER_PICTURES("characterPictures", Reach.SHAREABLE),
    VIDEO("video", Reach.MASTER),
    EQUIPPED_TABLE("equippedTable", Reach.ACCOUNT),
    EARLY_ACCESS("earlyAccess", Reach.ACCOUNT),
    PRIORITY_SUPPORT("prioritySupport", Reach.ACCOUNT);

    public enum Reach { SHAREABLE, MASTER, ACCOUNT }

    private final String key;
    private final Reach reach;

    Feature(String key, Reach reach) {
        this.key = key;
        this.reach = reach;
    }

    public String key() {
        return key;
    }

    public Reach reach() {
        return reach;
    }

    public static Optional<Feature> fromKey(String key) {
        return Arrays.stream(values()).filter(feature -> feature.key.equals(key)).findFirst();
    }
}
