package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.dto.adventure.AdventureDto;
import fr.barlords.dndgateapi.client.dto.adventure.master.AcceptMasterInvitationRequest;
import fr.barlords.dndgateapi.client.dto.adventure.master.MasterClaimDto;
import fr.barlords.dndgateapi.client.dto.adventure.master.MasterInvitationDto;
import fr.barlords.dndgateapi.client.dto.adventure.master.MasterVoteRequest;
import fr.barlords.dndgateapi.client.mapper.AdventureDtoMapper;
import fr.barlords.dndgateapi.client.mapper.MasterClaimDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.IssuedMasterInvitation;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterClaimApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterInvitationApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterStepDownApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

/** Changing the game master of an adventure: stepping down, handing over by invitation, asking for the role. */
@Tag(name = "Game master")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/adventures")
public class AdventureMasterController {

    private final AuthVerifierService authVerifierService;

    private final MasterStepDownApi masterStepDownApi;

    private final MasterInvitationApi masterInvitationApi;

    private final MasterClaimApi masterClaimApi;

    // ---- the game master steps down ----

    @PostMapping("/{id}/master/step-down")
    @ResponseStatus(OK)
    public AdventureDto stepDownAsMaster(@PathVariable String id, @Valid @RequestBody DeletionConfirmationRequest request) {
        return AdventureDtoMapper.toDto(masterStepDownApi.stepDown(
                authVerifierService.currentAccountId(), UuidValidator.validate(id), request.confirmation()));
    }

    // ---- the game master invites someone to take over ----

    @PostMapping("/{id}/master/invitation")
    @ResponseStatus(CREATED)
    public MasterInvitationDto inviteMaster(@PathVariable String id) {
        IssuedMasterInvitation invitation = masterInvitationApi.invite(authVerifierService.currentAccountId(), UuidValidator.validate(id));

        return new MasterInvitationDto(invitation.code(), invitation.expiresAt());
    }

    @DeleteMapping("/{id}/master/invitation")
    @ResponseStatus(NO_CONTENT)
    public void revokeMasterInvitation(@PathVariable String id) {
        masterInvitationApi.revoke(authVerifierService.currentAccountId(), UuidValidator.validate(id));
    }

    @PostMapping("/master/accept")
    @ResponseStatus(OK)
    public AdventureDto acceptMasterInvitation(@Valid @RequestBody AcceptMasterInvitationRequest request) {
        return AdventureDtoMapper.toDto(masterInvitationApi.accept(authVerifierService.currentAccountId(), request.code().trim()));
    }

    // ---- a player asks for the role, the other players vote ----

    @PostMapping("/{id}/master/claim")
    @ResponseStatus(CREATED)
    public MasterClaimDto claimMasterRole(@PathVariable String id) {
        return MasterClaimDtoMapper.toDto(masterClaimApi.claim(authVerifierService.currentAccountId(), UuidValidator.validate(id)));
    }

    @GetMapping("/{id}/master/claim")
    @ResponseStatus(OK)
    public MasterClaimDto getMasterClaim(@PathVariable String id) {
        return masterClaimApi.findOpen(authVerifierService.currentAccountId(), UuidValidator.validate(id))
                .map(MasterClaimDtoMapper::toDto)
                .orElseThrow(MasterClaimNotFoundException::new);
    }

    @PutMapping("/{id}/master/claim/vote")
    @ResponseStatus(OK)
    public MasterClaimDto voteOnMasterClaim(@PathVariable String id, @Valid @RequestBody MasterVoteRequest request) {
        return MasterClaimDtoMapper.toDto(masterClaimApi.vote(
                authVerifierService.currentAccountId(), UuidValidator.validate(id), request.approve()));
    }

    @DeleteMapping("/{id}/master/claim")
    @ResponseStatus(NO_CONTENT)
    public void cancelMasterClaim(@PathVariable String id) {
        masterClaimApi.cancel(authVerifierService.currentAccountId(), UuidValidator.validate(id));
    }
}
