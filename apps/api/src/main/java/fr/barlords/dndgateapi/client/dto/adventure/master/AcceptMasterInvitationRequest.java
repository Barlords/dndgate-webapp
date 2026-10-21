package fr.barlords.dndgateapi.client.dto.adventure.master;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AcceptMasterInvitationRequest(
        @NotBlank @Size(max = 200) String code
) {
}
