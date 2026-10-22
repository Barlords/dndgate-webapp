package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.PageDto;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.client.dto.message.MessageCreationRequest;
import fr.barlords.dndgateapi.client.dto.message.MessageDto;
import fr.barlords.dndgateapi.client.mapper.MessageDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageFinderApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@Tag(name = "Messages")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/messages")
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate;

    private final AuthVerifierService authVerifierService;

    private final MessageCreatorApi messageCreatorApi;

    private final MessageFinderApi messageFinderApi;

    @GetMapping
    @ResponseStatus(OK)
    public PageDto<MessageDto> getMessageByAdventureId(@RequestParam String adventureId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return PageDto.of(messageFinderApi.findByAdventureId(authVerifierService.currentAccountId(), UuidValidator.validate(adventureId),
                PageRequest.of(page, size)), MessageDtoMapper::toDto);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public MessageDto sendMessage(@Valid @RequestBody MessageCreationRequest request) {
        Message message = messageCreatorApi.create(
                authVerifierService.currentAccountId(),
                MessageDtoMapper.creationRequestToDomain(request)
        );

        // Broadcast the message to the subscribers of the adventure
        messagingTemplate.convertAndSend("/topic/adventure/chat/"+message.getAdventureId().toString(), message);

        return MessageDtoMapper.toDto(message);
    }
}


