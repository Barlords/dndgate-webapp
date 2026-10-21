package fr.barlords.dndgateapi.client.dto.adventure.master;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.Instant;

/** The code is only shown now: share it with the person who will take over. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MasterInvitationDto(String code, Instant expiresAt) {
}
