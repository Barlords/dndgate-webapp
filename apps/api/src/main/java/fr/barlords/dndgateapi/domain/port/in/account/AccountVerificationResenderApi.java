package fr.barlords.dndgateapi.domain.port.in.account;

public interface AccountVerificationResenderApi {

    /**
     * Sends a new confirmation e-mail to a pending signup; the previous code stops working and the expiry restarts.
     * <p>
     * It never tells whether the address is waiting for a confirmation: nothing happens when it is unknown or already
     * has an account, or when a mail was sent a moment ago.
     */
    void resend(String email);

}
