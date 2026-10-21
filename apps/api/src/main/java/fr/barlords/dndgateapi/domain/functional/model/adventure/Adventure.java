package fr.barlords.dndgateapi.domain.functional.model.adventure;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.RandomStringGenerator;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class Adventure {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID ownerId;

    @With
    String name;

    @With
    String description;

    @With
    Integer maxPlayer;

    @Builder.Default
    @With
    List<UUID> players = new ArrayList<>();

    @Builder.Default
    @With
    AdventureStatus status = AdventureStatus.NOT_STARTED;

    @With
    AdventureVisibility visibility;

    @With
    String postalCode;

    @With
    String city;

    @Builder.Default
    @With
    String invitationCode = RandomStringGenerator.generateAlphanumericString(10);

    /** Set while the adventure has no game master (they stepped down or deleted their account): nobody has the rights of a master. */
    @With
    Instant masterVacantSince;

    /** Version read from the storage, null while the adventure has never been saved: it detects concurrent changes. */
    @With
    Long version;

    public boolean isMyOwner(Account account) {
        return isMaster(account.getId());
    }

    public boolean isMaster(UUID accountId) {
        return !isMasterVacant() && accountId.equals(this.ownerId);
    }

    public boolean isMasterVacant() {
        return masterVacantSince != null;
    }

    /** The game master can only change in an adventure that is still alive. */
    public boolean isTransferable() {
        return AdventureStatus.NOT_STARTED.equals(this.status) || AdventureStatus.ONGOING.equals(this.status);
    }

    public boolean isParticipant(UUID accountId) {
        return accountId.equals(this.ownerId) || this.players.contains(accountId);
    }

    /** Players can only join before the adventure starts. */
    public boolean isOpenToNewPlayers() {
        return AdventureStatus.NOT_STARTED.equals(this.status);
    }

    public boolean isFull() {
        return maxPlayer != null && players.size() >= maxPlayer;
    }

    public boolean hasBeenPlayed() {
        return AdventureStatus.ONGOING.equals(this.status) || AdventureStatus.FINISH.equals(this.status);
    }

    public boolean isVisibleTo(UUID accountId) {
        return AdventureVisibility.PUBLIC.equals(this.visibility) || isParticipant(accountId);
    }
}
