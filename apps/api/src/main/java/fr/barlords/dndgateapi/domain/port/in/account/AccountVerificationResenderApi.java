package fr.barlords.dndgateapi.domain.port.in.account;

public interface AccountVerificationResenderApi {

    /**
     * Sends a new verification e-mail to an account that is not verified yet; the previous code stops working.
     * <p>
     * It never tells whether the address is registered: nothing happens when it is unknown, already verified,
     * or when a mail was sent a moment ago.
     */
    void resend(String email);

}
