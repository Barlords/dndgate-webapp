package fr.barlords.dndgateapi.domain.port.in.account;

public interface AccountVerifierApi {

    /**
     * Activates the account whose e-mail address is verified by the code received in the mail.
     * A code works once, and only until it expires.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException
     *         when the code is wrong, expired or already used, or when the address is unknown
     */
    void verify(String email, String code);

}
