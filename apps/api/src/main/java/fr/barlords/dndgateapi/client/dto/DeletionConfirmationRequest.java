package fr.barlords.dndgateapi.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sent with every irreversible deletion: the name of what is deleted, retyped,
 * or the password of the account when the account itself is deleted.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record DeletionConfirmationRequest(
        @NotNull @Size(max = 300) String confirmation
) { }
