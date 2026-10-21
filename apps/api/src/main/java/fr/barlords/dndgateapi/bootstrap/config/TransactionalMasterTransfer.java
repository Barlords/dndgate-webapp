package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.IssuedMasterInvitation;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimDetails;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterClaimApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterInvitationApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterStepDownApi;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

/**
 * The role of game master changes together with the invitation or the vote that decided it: either everything
 * is written or nothing is. These decorators give each operation its own transaction, replayed on conflict.
 */
public final class TransactionalMasterTransfer {

    private TransactionalMasterTransfer() {
    }

    @RequiredArgsConstructor
    public static class StepDown implements MasterStepDownApi {

        private final MasterStepDownApi delegate;

        private final RetryingTransaction transaction;

        @Override
        public Adventure stepDown(UUID accountId, UUID adventureId, String confirmation) {
            return transaction.execute(() -> delegate.stepDown(accountId, adventureId, confirmation));
        }
    }

    @RequiredArgsConstructor
    public static class Invitation implements MasterInvitationApi {

        private final MasterInvitationApi delegate;

        private final RetryingTransaction transaction;

        @Override
        public IssuedMasterInvitation invite(UUID accountId, UUID adventureId) {
            return transaction.execute(() -> delegate.invite(accountId, adventureId));
        }

        @Override
        public void revoke(UUID accountId, UUID adventureId) {
            transaction.run(() -> delegate.revoke(accountId, adventureId));
        }

        @Override
        public Adventure accept(UUID accountId, String code) {
            return transaction.execute(() -> delegate.accept(accountId, code));
        }
    }

    @RequiredArgsConstructor
    public static class Claim implements MasterClaimApi {

        private final MasterClaimApi delegate;

        private final RetryingTransaction transaction;

        @Override
        public MasterClaimDetails claim(UUID accountId, UUID adventureId) {
            return transaction.execute(() -> delegate.claim(accountId, adventureId));
        }

        @Override
        public MasterClaimDetails vote(UUID accountId, UUID adventureId, boolean approves) {
            return transaction.execute(() -> delegate.vote(accountId, adventureId, approves));
        }

        @Override
        public void cancel(UUID accountId, UUID adventureId) {
            transaction.run(() -> delegate.cancel(accountId, adventureId));
        }

        @Override
        public Optional<MasterClaimDetails> findOpen(UUID accountId, UUID adventureId) {
            return transaction.execute(() -> delegate.findOpen(accountId, adventureId));
        }
    }
}
