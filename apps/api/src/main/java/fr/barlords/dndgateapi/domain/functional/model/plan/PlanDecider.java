package fr.barlords.dndgateapi.domain.functional.model.plan;

/** Whose plan decided a refusal: the plan of the caller, or the plan of the game master of the adventure. */
public enum PlanDecider {
    ACCOUNT("account"), MASTER("master");

    private final String key;

    PlanDecider(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
