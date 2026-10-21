package fr.barlords.dndgateapi.client.dto.account;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.client.validator.ValidPassword;
import fr.barlords.dndgateapi.client.validator.ValidPseudo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changes to an account. The current password is always required; each other field is optional
 * and only what is given is changed.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AccountUpdateRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @Size(min = 5, max = 50) @Email String email,
        @ValidPseudo String pseudo,
        @ValidPassword String newPassword
) { }
