package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.server.entity.AccountEntity;

public interface AccountEntityMapper {

    static Account toDomain(AccountEntity entity) {
        return Account.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .password(entity.getPassword())
                .pseudo(entity.getPseudo())
                .activated(entity.isActivated())
                .pendingEmail(entity.getPendingEmail())
                .pendingEmailCode(entity.getPendingEmailCode())
                .pendingEmailExpiresAt(entity.getPendingEmailExpiresAt())
                .founder(entity.isFounder())
                .anonymizedAt(entity.getAnonymizedAt())
                .version(entity.getVersion())
                .build();
    }

    static AccountEntity fromDomain(Account domain) {
        return AccountEntity.builder()
                .id(domain.getId())
                .email(domain.getEmail())
                .password(domain.getPassword())
                .pseudo(domain.getPseudo())
                .activated(domain.isActivated())
                .pendingEmail(domain.getPendingEmail())
                .pendingEmailCode(domain.getPendingEmailCode())
                .pendingEmailExpiresAt(domain.getPendingEmailExpiresAt())
                .founder(domain.isFounder())
                .anonymizedAt(domain.getAnonymizedAt())
                .version(domain.getVersion())
                .build();
    }

}
