package fr.barlords.dndgateapi.domain.port.in.account;

import java.util.UUID;

public interface AccountAnonymizerApi {

    /**
     * Deletes the account of a person: what identifies them (pseudo, e-mail, password, sessions, spell books) is
     * erased, while what other players rely on (characters, messages, votes, ratings) keeps pointing to the same
     * account id, which is now anonymous.
     * <p>
     * Adventures that have not started and that the account owned are aborted and hidden, the seats it took in
     * adventures that have not started are freed.
     * <p>
     * It cannot be undone, so the password of the account must be given again.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException when the password is wrong
     */
    void anonymize(UUID accountId, String password);

}
