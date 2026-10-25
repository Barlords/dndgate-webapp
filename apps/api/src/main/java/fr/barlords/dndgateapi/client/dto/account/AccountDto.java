package fr.barlords.dndgateapi.client.dto.account;

import java.util.UUID;

public record AccountDto(
        UUID id,
        String email,
        String pseudo,
        /** The address waiting for its confirmation, if any: the account keeps {@code email} until then. */
        String pendingEmail,
        boolean founder,
        /** {@code dungeon-master}, {@code legend} or null: the badge of the plan in force. */
        String planBadge
) { }