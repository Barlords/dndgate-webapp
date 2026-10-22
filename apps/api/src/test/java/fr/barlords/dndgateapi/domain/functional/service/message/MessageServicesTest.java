package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageServicesTest {

    @Mock
    private MessagePersistenceSpi spi;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    private final UUID accountId = UUID.randomUUID();

    private final UUID adventureId = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-05-01T10:15:30Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final PageRequest request = new PageRequest(0, 20);

    @Test
    void participant_can_read_messages() {
        Page<Message> messages = new Page<>(List.of(Message.builder().adventureId(adventureId).build()), request, 1);
        when(spi.findByAdventureId(adventureId, request)).thenReturn(messages);

        Assertions.assertEquals(messages, new MessageFinderService(spi, accessVerifier).findByAdventureId(accountId, adventureId, request));
        verify(accessVerifier).verifyParticipant(accountId, adventureId);
    }

    @Test
    void stranger_cannot_read_messages() {
        doThrow(new AdventureAccessDeniedException()).when(accessVerifier).verifyParticipant(accountId, adventureId);

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> new MessageFinderService(spi, accessVerifier).findByAdventureId(accountId, adventureId, request));
        verify(spi, never()).findByAdventureId(any(), any());
    }

    @Test
    void participant_can_send_a_message_and_sender_is_forced_to_the_caller() {
        Message message = Message.builder().adventureId(adventureId).senderId(UUID.randomUUID()).content("hello").build();
        when(spi.save(any(Message.class))).thenAnswer(i -> i.getArguments()[0]);

        Message saved = new MessageCreatorService(spi, accessVerifier, clock, QuantityLimits.defaults()).create(accountId, message);

        Assertions.assertEquals(accountId, saved.getSenderId());
    }

    @Test
    void stranger_cannot_send_a_message() {
        Message message = Message.builder().adventureId(adventureId).content("hello").build();
        doThrow(new AdventureAccessDeniedException()).when(accessVerifier).verifyParticipant(accountId, adventureId);

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> new MessageCreatorService(spi, accessVerifier, clock, QuantityLimits.defaults()).create(accountId, message));
        verify(spi, never()).save(any());
    }

    @Test
    void the_send_date_is_fixed_by_the_server_clock_whatever_the_message_carries() {
        Message forged = Message.builder().adventureId(adventureId).content("hello")
                .sendDate(Instant.parse("2199-12-31T00:00:00Z")).build();
        when(spi.save(any(Message.class))).thenAnswer(i -> i.getArguments()[0]);

        Message saved = new MessageCreatorService(spi, accessVerifier, clock, QuantityLimits.defaults()).create(accountId, forged);

        Assertions.assertEquals(NOW, saved.getSendDate());
    }

    @Test
    void the_send_date_is_set_when_the_message_carries_none() {
        Message message = Message.builder().adventureId(adventureId).content("hello").build();
        when(spi.save(any(Message.class))).thenAnswer(i -> i.getArguments()[0]);

        Assertions.assertEquals(NOW, new MessageCreatorService(spi, accessVerifier, clock, QuantityLimits.defaults()).create(accountId, message).getSendDate());
    }
}
