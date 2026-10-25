package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Arrays;
import java.util.Optional;

public enum BillingInterval {
    MONTHLY("monthly"), YEARLY("yearly");

    private final String key;

    BillingInterval(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<BillingInterval> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
