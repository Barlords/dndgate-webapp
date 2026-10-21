package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.in.account.AccountEmailChangeConfirmerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class AccountEmailChangeConfirmerService implements AccountEmailChangeConfirmerApi {

    private final AccountPersistenceSpi spi;

    private final AccountCheckerService accountCheckerService;

    private final Clock clock;

    @Override
    public void confirm(String newEmail, String code) {
        Account account = spi.findAllByPendingEmail(newEmail).stream()
                .filter(waiting -> waiting.hasValidPendingEmailCode(code, clock.instant()))
                .findFirst()
                .orElseThrow(InvalidVerificationCodeException::new);

        // nothing reserved the address while the link waited: someone may have registered with it since
        accountCheckerService.emailIsAvailable(account.withEmail(newEmail));

        spi.save(account
                        .withEmail(newEmail)
                        .withPendingEmail(null)
                        .withPendingEmailCode(null)
                        .withPendingEmailExpiresAt(null))
                .getOrElseThrow(DataNotSaveException::new);
    }

}
