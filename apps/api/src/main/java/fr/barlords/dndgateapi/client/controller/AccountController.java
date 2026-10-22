package fr.barlords.dndgateapi.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import fr.barlords.dndgateapi.client.dto.DeletionConfirmationRequest;
import fr.barlords.dndgateapi.client.dto.account.AccountUpdateRequest;
import fr.barlords.dndgateapi.client.dto.account.VerificationEmailRequest;
import fr.barlords.dndgateapi.client.dto.account.VerificationRequest;
import fr.barlords.dndgateapi.client.dto.auth.AuthDto;
import fr.barlords.dndgateapi.client.dto.auth.RefreshRequest;
import fr.barlords.dndgateapi.client.dto.account.PublicAccountDto;
import fr.barlords.dndgateapi.client.dto.account.AccountCreationRequest;
import fr.barlords.dndgateapi.client.dto.account.AccountDto;
import fr.barlords.dndgateapi.client.dto.account.AccountLogRequest;
import fr.barlords.dndgateapi.client.dto.account.SignupAcceptedDto;
import fr.barlords.dndgateapi.client.mapper.AuthDtoMapper;
import fr.barlords.dndgateapi.client.mapper.AccountDtoMapper;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.client.service.ClientAddresses;
import fr.barlords.dndgateapi.client.validator.UuidValidator;
import fr.barlords.dndgateapi.domain.functional.exception.account.TokenNotValidException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.port.in.account.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@Tag(name = "Accounts")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/accounts")
public class AccountController {

    private final AuthVerifierService authVerifierService;

    private final AccountCreatorApi accountCreatorApi;

    private final AccountUpdaterApi accountUpdaterApi;

    private final AccountFinderApi accountFinderApi;

    private final AccountLoggerApi accountLoggerApi;

    private final AccountAnonymizerApi accountAnonymizerApi;

    private final AccountVerifierApi accountVerifierApi;

    private final AccountEmailChangeConfirmerApi accountEmailChangeConfirmerApi;

    private final AccountVerificationResenderApi accountVerificationResenderApi;

    private final AccountSessionRefresherApi accountSessionRefresherApi;

    private final AccountSessionRevokerApi accountSessionRevokerApi;

    @GetMapping("/me")
    @ResponseStatus(OK)
    public AccountDto getMyProfile() {
        return accountFinderApi.findById(authVerifierService.currentAccountId())
                .map(AccountDtoMapper::toDto)
                .orElseThrow(TokenNotValidException::new);
    }

    @GetMapping("/id/{id}")
    @ResponseStatus(OK)
    public PublicAccountDto getAccountById(@PathVariable String id) {
        return accountFinderApi.findById(UuidValidator.validate(id))
                .map(AccountDtoMapper::toPublicDto)
                .orElseThrow(AccountNotFoundException::new);
    }

    @GetMapping("/pseudo/{pseudo}")
    @ResponseStatus(OK)
    public PublicAccountDto getAccountByPseudo(@PathVariable String pseudo) {
        return accountFinderApi.findByPseudo(pseudo)
                .map(AccountDtoMapper::toPublicDto)
                .orElseThrow(AccountNotFoundException::new);
    }

    @PostMapping
    @ResponseStatus(ACCEPTED)
    @Operation(summary = "Sign up",
            description = "Records a pending signup and sends the confirmation link: the account only exists once the link "
                    + "is followed. The answer is the same whether the address is free or already registered; "
                    + "only a pseudo already used by an account answers 409.")
    public SignupAcceptedDto createAccount(@Valid @RequestBody AccountCreationRequest request) {
        accountCreatorApi.create(AccountDtoMapper.creationRequestToDomain(request));
        return SignupAcceptedDto.INSTANCE;
    }

    @PatchMapping
    @ResponseStatus(OK)
    public AccountDto patchAccount(@Valid @RequestBody AccountUpdateRequest request, HttpServletRequest httpRequest) {
        return AccountDtoMapper.toDto(
                accountUpdaterApi.update(
                        authVerifierService.currentAccountId(),
                        request.currentPassword(),
                        AccountDtoMapper.toChanges(request),
                        ClientAddresses.of(httpRequest)
                )
        );
    }

    @DeleteMapping
    @ResponseStatus(NO_CONTENT)
    public void deleteMyAccount(@Valid @RequestBody DeletionConfirmationRequest request, HttpServletRequest httpRequest) {
        accountAnonymizerApi.anonymize(authVerifierService.currentAccountId(), request.confirmation(), ClientAddresses.of(httpRequest));
    }

    @PostMapping(path = "/login")
    @ResponseStatus(OK)
    public AuthDto login(@Valid @RequestBody AccountLogRequest log, HttpServletRequest httpRequest) {
        return AuthDtoMapper.toDto(
                accountLoggerApi.login(
                        log.pseudo().trim(),
                        log.password(),
                        ClientAddresses.of(httpRequest)));
    }

    @PostMapping(path = "/refresh")
    @ResponseStatus(OK)
    public AuthDto refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthDtoMapper.toDto(accountSessionRefresherApi.refresh(request.refreshToken()));
    }

    @PostMapping(path = "/logout")
    @ResponseStatus(NO_CONTENT)
    public void logout() {
        accountSessionRevokerApi.logout(authVerifierService.currentSessionId());
    }

    @PostMapping(path = "/logout-all")
    @ResponseStatus(NO_CONTENT)
    public void logoutAll() {
        accountSessionRevokerApi.logoutAll(authVerifierService.currentAccountId());
    }

    /**
     * A POST and not a GET: the link in the mail leads to a page of the site which calls this route,
     * so that merely following the link (mail scanners, link previews) does not change anything.
     */
    @PostMapping("/verify")
    @ResponseStatus(NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerificationRequest request) {
        accountVerifierApi.verify(AccountDtoMapper.normalizeEmail(request.email()), request.code().trim());
    }

    /**
     * Same idea as {@code /verify}: the link in the mail leads to a page of the site which calls this route.
     * No token is needed, the link may be opened on another device than the one that asked for the change.
     */
    @PostMapping("/email/confirm")
    @ResponseStatus(NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody VerificationRequest request) {
        accountEmailChangeConfirmerApi.confirm(AccountDtoMapper.normalizeEmail(request.email()), request.code().trim());
    }

    /** Always 202, whether the address is registered or not. */
    @PostMapping("/verification-email")
    @ResponseStatus(ACCEPTED)
    public void resendVerificationEmail(@Valid @RequestBody VerificationEmailRequest request) {
        accountVerificationResenderApi.resend(AccountDtoMapper.normalizeEmail(request.email()));
    }

}
