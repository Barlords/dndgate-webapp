package fr.barlords.dndgateapi.client.dto.account;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AccountLogRequest(
        @NotBlank @Size(max = 100) String pseudo,
        @NotBlank @Size(max = 200) String password
) {

}
