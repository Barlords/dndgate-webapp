package fr.barlords.dndgateapi.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebRTCMessage {
    @NotNull
    @Pattern(regexp = "offer|answer|candidate")
    private String type;

    /** An SDP description or an ICE candidate: a few kilobytes at most. */
    @NotNull
    @Size(max = 20000)
    private String content;

    @NotNull
    @Size(max = 36)
    private String adventureId;

    /** Set by the server on the broadcast message (the authenticated sender); never read from the request. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String accountId;
}

