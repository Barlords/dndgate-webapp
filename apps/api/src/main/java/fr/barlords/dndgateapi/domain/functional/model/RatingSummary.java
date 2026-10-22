package fr.barlords.dndgateapi.domain.functional.model;

/**
 * What an account's ratings add up to: the average is null when the account has no rating.
 */
public record RatingSummary(Double average, long count) {
}
