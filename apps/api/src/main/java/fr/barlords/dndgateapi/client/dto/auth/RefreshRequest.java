package fr.barlords.dndgateapi.client.dto.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RefreshRequest(
        @NotBlank @Size(max = 200) String refreshToken
) { }
