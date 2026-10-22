package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.in.account.AccountVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class AccountVerifierService implements AccountVerifierApi {

    private final AccountPersistenceSpi spi;

    private final Clock clock;

    @Override
    public void verify(String email, String code) {
        Account account = spi.findByEmail(email)
                .filter(found -> found.hasValidVerificationCode(code, clock.instant()))
                .orElseThrow(InvalidVerificationCodeException::new);

        spi.save(account
                        .withVerificationCode(null)
                        .withVerificationExpiresAt(null)
                        .withActivated(true));
    }

}
