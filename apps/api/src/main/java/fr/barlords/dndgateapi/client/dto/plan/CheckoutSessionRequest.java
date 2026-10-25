package fr.barlords.dndgateapi.client.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutSessionRequest(
        @NotBlank @Size(min = 1, max = 50) String planId,
        @NotNull @Pattern(regexp = "monthly|yearly")
        @Schema(allowableValues = {"monthly", "yearly"}) String interval) {
}
