package fr.barlords.dndgateapi.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Confirmation of the deletion of something that has no name to retype (a note).
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ExplicitDeletionRequest(
        @NotNull @AssertTrue(message = "the deletion must be confirmed") Boolean confirm
) { }
