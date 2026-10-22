package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.account.AccountCreationRequest;
import fr.barlords.dndgateapi.client.dto.account.AccountUpdateRequest;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;
import fr.barlords.dndgateapi.client.dto.account.PublicAccountDto;
import fr.barlords.dndgateapi.client.dto.account.AccountDto;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;

import java.util.Locale;


public interface AccountDtoMapper {

    /** An address designates one account whatever its case: it is trimmed and lower-cased as soon as it comes in. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static AccountDto toDto(Account domain) {
        return new AccountDto(
                domain.getId(),
                domain.getEmail(),
                domain.getPseudo(),
                domain.getPendingEmail()
        );
    }

    static PublicAccountDto toPublicDto(Account domain) {
        return new PublicAccountDto(
                domain.getId(),
                domain.getPseudo(),
                domain.isAnonymized()
        );
    }

    static AccountChanges toChanges(AccountUpdateRequest request) {
        return new AccountChanges(
                request.email() == null ? null : normalizeEmail(request.email()),
                request.pseudo() == null ? null : request.pseudo().trim(),
                request.newPassword()
        );
    }

    static Account creationRequestToDomain(AccountCreationRequest request) {
        return Account.builder()
                .email(normalizeEmail(request.email()))
                .password(request.password())
                .pseudo(request.pseudo().trim())
                .build();
    }
}
