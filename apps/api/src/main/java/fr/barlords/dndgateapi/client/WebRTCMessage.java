package fr.barlords.dndgateapi.client;

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

    /** Ignored: the sender is the authenticated caller. */
    @Size(max = 36)
    private String accountId;
}

