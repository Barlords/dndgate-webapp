package fr.barlords.dndgateapi.domain.functional.model.account;

/**
 * What the owner of an account wants to change. A field left null is not changed.
 * The new password is in clear: it is hashed when it is applied.
 */
public record AccountChanges(String email, String pseudo, String newPassword) {
}
