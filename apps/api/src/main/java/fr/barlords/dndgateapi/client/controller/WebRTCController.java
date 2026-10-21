package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.WebRTCMessage;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@Tag(name = "WebRTC")
@RestController
@RequiredArgsConstructor
@RequestMapping("/webrtc")
public class WebRTCController {

    private final SimpMessagingTemplate messagingTemplate;

    private final AuthVerifierService authVerifierService;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @PostMapping("/offer")
    @ResponseStatus(NO_CONTENT)
    public void sendOffer(@Valid @RequestBody WebRTCMessage offer) {
        broadcast(offer);
    }

    @PostMapping("/answer")
    @ResponseStatus(NO_CONTENT)
    public void sendAnswer(@Valid @RequestBody WebRTCMessage answer) {
        broadcast(answer);
    }

    @PostMapping("/candidate")
    @ResponseStatus(NO_CONTENT)
    public void sendCandidate(@Valid @RequestBody WebRTCMessage candidate) {
        broadcast(candidate);
    }

    private void broadcast(WebRTCMessage message) {
        var adventureId = UuidValidator.validate(message.getAdventureId());
        adventureAccessVerifierApi.verifyParticipant(authVerifierService.currentAccountId(), adventureId);

        // The sender is the authenticated caller, never the value provided by the client
        message.setAccountId(authVerifierService.currentAccountId().toString());

        messagingTemplate.convertAndSend("/topic/adventure/webrtc/" + adventureId, message);
    }
}
