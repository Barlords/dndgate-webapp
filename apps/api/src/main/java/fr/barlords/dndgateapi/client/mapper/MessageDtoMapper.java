package fr.barlords.dndgateapi.client.mapper;


import fr.barlords.dndgateapi.client.dto.message.MessageCreationRequest;
import fr.barlords.dndgateapi.client.dto.message.MessageDto;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.model.Message;


public interface MessageDtoMapper {

    static MessageDto toDto(Message domain) {
        return new MessageDto(
                domain.getId(),
                domain.getAdventureId(),
                domain.getSenderId(),
                domain.getContent(),
                domain.getSendDate()
        );
    }

    static Message creationRequestToDomain(MessageCreationRequest request) {
        return Message.builder()
                .adventureId(UuidValidator.validate(request.adventureId()))
                .content(request.content().trim())
                .sendDate(request.sendDate())
                .build();
    }

}
