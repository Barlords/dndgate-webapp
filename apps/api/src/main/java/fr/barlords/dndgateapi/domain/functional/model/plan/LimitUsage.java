package fr.barlords.dndgateapi.domain.functional.model.plan;

/** How much of a limit is used, and the maximum the plan allows. */
public record LimitUsage(long used, int max) {
}
