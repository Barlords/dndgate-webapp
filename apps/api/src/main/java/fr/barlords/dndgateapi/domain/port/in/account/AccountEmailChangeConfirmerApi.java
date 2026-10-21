package fr.barlords.dndgateapi.domain.port.in.account;

public interface AccountEmailChangeConfirmerApi {

    /**
     * Makes {@code newEmail} the address of the account that asked for it, once its owner proves it by the code
     * received there. A code works once, and only until it expires.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException
     *         when the code is wrong, expired or already used, or when nobody is waiting for this address
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException
     *         when the address was taken by another account in the meantime
     */
    void confirm(String newEmail, String code);

}
