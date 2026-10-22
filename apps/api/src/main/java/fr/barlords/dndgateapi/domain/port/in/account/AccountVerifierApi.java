package fr.barlords.dndgateapi.domain.port.in.account;

public interface AccountVerifierApi {

    /**
     * Confirms a pending signup: creates the account, already activated, from the signup that holds this code and
     * forgets the signup. A code works once, and only until it expires.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException
     *         when the code is wrong, expired or already used, or when no signup waits on that address (or when the
     *         address got an account in the meantime: the answer never tells which)
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException
     *         when the pseudo was taken since the signup (the signup is forgotten)
     */
    void verify(String email, String code);

}
