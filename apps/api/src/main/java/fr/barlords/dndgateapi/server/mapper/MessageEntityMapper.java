package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.Message;
import fr.barlords.dndgateapi.server.entity.MessageEntity;

public interface MessageEntityMapper {

    static Message toDomain(MessageEntity entity) {
        return Message.builder()
                .id(entity.getId())
                .adventureId(entity.getAdventureId())
                .senderId(entity.getSenderId())
                .content(entity.getContent())
                .sendDate(entity.getSendDate())
                .build();
    }

    static MessageEntity fromDomain(Message domain) {
        return MessageEntity.builder()
                .id(domain.getId())
                .adventureId(domain.getAdventureId())
                .senderId(domain.getSenderId())
                .content(domain.getContent())
                .sendDate(domain.getSendDate())
                .build();
    }

}
