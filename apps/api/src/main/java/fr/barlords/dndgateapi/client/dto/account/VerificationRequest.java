package fr.barlords.dndgateapi.client.dto.account;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record VerificationRequest(
        @NotBlank @Size(max = 100) String email,
        @NotBlank @Size(max = 100) String code
) { }
