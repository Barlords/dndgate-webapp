package fr.barlords.dndgateapi.client.dto.account;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record VerificationEmailRequest(
        @NotBlank @Size(max = 100) @Email String email
) { }
