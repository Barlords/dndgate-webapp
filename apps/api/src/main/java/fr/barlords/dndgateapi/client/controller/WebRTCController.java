package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.WebRTCMessage;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WebRTC")
@RestController
@RequiredArgsConstructor
@RequestMapping("/webrtc")
public class WebRTCController {

    private final SimpMessagingTemplate messagingTemplate;

    private final AuthVerifierService authVerifierService;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @PostMapping("/offer")
    public ResponseEntity<String> sendOffer(@Valid @RequestBody WebRTCMessage offer) {
        broadcast(offer);
        return ResponseEntity.ok("Offer received and broadcasted");
    }

    @PostMapping("/answer")
    public ResponseEntity<String> sendAnswer(@Valid @RequestBody WebRTCMessage answer) {
        broadcast(answer);
        return ResponseEntity.ok("Answer received and broadcasted");
    }

    @PostMapping("/candidate")
    public ResponseEntity<String> sendCandidate(@Valid @RequestBody WebRTCMessage candidate) {
        broadcast(candidate);
        return ResponseEntity.ok("Candidate received and broadcasted");
    }

    private void broadcast(WebRTCMessage message) {
        var adventureId = UuidValidator.validate(message.getAdventureId());
        adventureAccessVerifierApi.verifyParticipant(authVerifierService.currentAccountId(), adventureId);

        // The sender is the authenticated caller, never the value provided by the client
        message.setAccountId(authVerifierService.currentAccountId().toString());

        messagingTemplate.convertAndSend("/topic/adventure/webrtc/" + adventureId, message);
    }
}
