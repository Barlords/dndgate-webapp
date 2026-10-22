package fr.barlords.dndgateapi.client.controller;

import fr.barlords.dndgateapi.client.WebRTCMessage;
import fr.barlords.dndgateapi.client.dto.message.MessageCreationRequest;
import fr.barlords.dndgateapi.client.dto.message.MessageDto;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageFinderApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

/** What goes through the WebSocket topics is a documented DTO, never an object of the domain. */
class BroadcastPayloadTest {

    private final SimpMessagingTemplate template = Mockito.mock(SimpMessagingTemplate.class);
    private final AuthVerifierService auth = Mockito.mock(AuthVerifierService.class);
    private final UUID accountId = UUID.randomUUID();
    private final UUID adventureId = UUID.randomUUID();

    @Test
    void a_chat_message_is_broadcast_as_the_message_dto() {
        Message created = Message.builder().adventureId(adventureId).senderId(accountId).content("hello")
                .sendDate(Instant.parse("2026-01-01T10:00:00Z")).build();
        MessageCreatorApi creator = Mockito.mock(MessageCreatorApi.class);
        Mockito.when(auth.currentAccountId()).thenReturn(accountId);
        Mockito.when(creator.create(Mockito.eq(accountId), Mockito.any())).thenReturn(created);
        var controller = new MessageController(template, auth, creator, Mockito.mock(MessageFinderApi.class));

        MessageDto answer = controller.sendMessage(new MessageCreationRequest(adventureId, "hello"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(template).convertAndSend(Mockito.eq("/topic/adventure/chat/" + adventureId), payload.capture());
        Assertions.assertInstanceOf(MessageDto.class, payload.getValue());
        Assertions.assertEquals(answer, payload.getValue());
    }

    @Test
    void a_webrtc_signal_is_broadcast_as_a_webrtc_message_stamped_with_the_caller() {
        Mockito.when(auth.currentAccountId()).thenReturn(accountId);
        var controller = new WebRTCController(template, auth, Mockito.mock(AdventureAccessVerifierApi.class));
        WebRTCMessage signal = new WebRTCMessage();
        signal.setType("offer");
        signal.setContent("sdp");
        signal.setAdventureId(adventureId.toString());

        controller.sendOffer(signal);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(template).convertAndSend(Mockito.eq("/topic/adventure/webrtc/" + adventureId), payload.capture());
        WebRTCMessage sent = Assertions.assertInstanceOf(WebRTCMessage.class, payload.getValue());
        Assertions.assertEquals(accountId.toString(), sent.getAccountId());
    }
}
