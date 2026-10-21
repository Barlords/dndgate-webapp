package fr.barlords.dndgateapi.client.dto.account;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.client.validator.ValidPassword;
import fr.barlords.dndgateapi.client.validator.ValidPseudo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AccountCreationRequest(
        @NotNull @Size(min = 5, max = 50) @Email String email,
        @NotNull @ValidPassword String password,
        @NotNull @ValidPseudo String pseudo
) {
}
