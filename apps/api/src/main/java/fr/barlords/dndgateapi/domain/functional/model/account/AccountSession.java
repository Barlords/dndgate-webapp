package fr.barlords.dndgateapi.domain.functional.model.account;

public record AccountSession(Account account, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
