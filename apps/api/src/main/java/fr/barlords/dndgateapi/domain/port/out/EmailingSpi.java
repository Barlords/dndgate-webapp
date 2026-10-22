package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;

public interface EmailingSpi {

    void sendAccountValidationEmail(Account account);

    /**
     * Sent to the owner of an address somebody tried to register with: registering answers the same whether the
     * address is known or not, so this mail is the only place the owner learns about it.
     */
    void sendSignupAttemptEmail(Account owner);

    /** Sent to the address the account asked to switch to ({@code pendingEmail}), with the link that confirms it. */
    void sendEmailChangeEmail(Account account);

    void sendInvitationCodeEmail(Account sender, Account receiver, String code);

    /**
     * Sent once the transaction in progress (if any) is committed: nothing leaves for a change that was rolled back,
     * and a change replayed after a conflict is told once.
     */
    void sendMasterTransferEmail(Account receiver, MasterTransferMessage message);

}
