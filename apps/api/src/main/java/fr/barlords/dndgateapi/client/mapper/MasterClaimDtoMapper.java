package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.adventure.master.MasterClaimDto;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimDetails;

public interface MasterClaimDtoMapper {

    static MasterClaimDto toDto(MasterClaimDetails details) {
        return new MasterClaimDto(
                details.claim().getId(),
                details.claim().getAdventureId(),
                details.claim().getCandidateId(),
                details.claim().getStatus(),
                details.claim().getExpiresAt(),
                details.voters().stream()
                        .map(voter -> new MasterClaimDto.Voter(voter, details.claim().getVotes().get(voter)))
                        .toList()
        );
    }
}
