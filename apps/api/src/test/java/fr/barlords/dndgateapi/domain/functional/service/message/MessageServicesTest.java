package fr.barlords.dndgateapi.domain.functional.service.message;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(spi.save(any(Message.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        Message saved = new MessageCreatorService(spi, accessVerifier).create(accountId, message);

        Assertions.assertEquals(accountId, saved.getSenderId());
    }

    @Test
    void stranger_cannot_send_a_message() {
        Message message = Message.builder().adventureId(adventureId).content("hello").build();
        doThrow(new AdventureAccessDeniedException()).when(accessVerifier).verifyParticipant(accountId, adventureId);

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> new MessageCreatorService(spi, accessVerifier).create(accountId, message));
        verify(spi, never()).save(any());
    }
}
