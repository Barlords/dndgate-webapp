package fr.barlords.dndgateapi.client.dto.account;

import java.util.UUID;

/**
 * Public view of an account: no personal data such as the email address.
 * An anonymized account (deleted by its owner) is flagged so that the pages can show "deleted account".
 */
public record PublicAccountDto(
        UUID id,
        String pseudo,
        boolean anonymized,
        boolean founder,
        /** {@code dungeon-master}, {@code legend} or null: the badge of the plan in force. */
        String planBadge
) { }
