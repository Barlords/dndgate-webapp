package fr.barlords.dndgateapi.domain.functional.model;

/**
 * The outcome of a create-or-update operation: the stored value, and whether this call created it.
 * The HTTP layer answers 201 for a creation and 200 for an update.
 */
public record Upserted<T>(T value, boolean created) {

    public static <T> Upserted<T> created(T value) {
        return new Upserted<>(value, true);
    }

    public static <T> Upserted<T> updated(T value) {
        return new Upserted<>(value, false);
    }
}
