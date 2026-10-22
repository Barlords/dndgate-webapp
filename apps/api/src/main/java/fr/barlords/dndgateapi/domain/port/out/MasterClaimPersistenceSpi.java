package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterClaimPersistenceSpi {

    /**
     * Throws AdventureConcurrentModificationException if the claim changed meanwhile, and
     * MasterClaimAlreadyOpenException if another claim is open for the adventure.
     */
    MasterClaim save(MasterClaim claim);

    /** The claim of the adventure that has not been closed, even if its time is over (the caller decides). */
    Optional<MasterClaim> findOpenByAdventureId(UUID adventureId);

    /** The claims the account made as a candidate that have not been closed, even if their time is over. */
    List<MasterClaim> findOpenByCandidateId(UUID candidateId);

    /** Every claim of the adventure, closed or not, with its votes. */
    void deleteByAdventureId(UUID adventureId);
}
