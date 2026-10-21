package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.auth.AuthDto;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;

public interface AuthDtoMapper {

    static AuthDto toDto(AccountSession session) {
        return new AuthDto(
                session.account().getId(),
                session.accessToken().value(),
                session.accessToken().expiresAt(),
                session.refreshToken().value(),
                session.refreshToken().expiresAt()
        );
    }

}
