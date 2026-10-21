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
                .verificationCode(entity.getVerificationCode())
                .verificationExpiresAt(entity.getVerificationExpiresAt())
                .pendingEmail(entity.getPendingEmail())
                .pendingEmailCode(entity.getPendingEmailCode())
                .pendingEmailExpiresAt(entity.getPendingEmailExpiresAt())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .lockedUntil(entity.getLockedUntil())
                .anonymizedAt(entity.getAnonymizedAt())
                .build();
    }

    static AccountEntity fromDomain(Account domain) {
        return AccountEntity.builder()
                .id(domain.getId())
                .email(domain.getEmail())
                .password(domain.getPassword())
                .pseudo(domain.getPseudo())
                .activated(domain.isActivated())
                .verificationCode(domain.getVerificationCode())
                .verificationExpiresAt(domain.getVerificationExpiresAt())
                .pendingEmail(domain.getPendingEmail())
                .pendingEmailCode(domain.getPendingEmailCode())
                .pendingEmailExpiresAt(domain.getPendingEmailExpiresAt())
                .failedLoginAttempts(domain.getFailedLoginAttempts())
                .lockedUntil(domain.getLockedUntil())
                .anonymizedAt(domain.getAnonymizedAt())
                .build();
    }

}
