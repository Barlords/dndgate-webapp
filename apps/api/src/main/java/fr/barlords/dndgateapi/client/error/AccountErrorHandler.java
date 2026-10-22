package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.account.*;
import org.springframework.http.HttpHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidRefreshTokenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.time.Instant;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountErrorHandler {

    @ExceptionHandler(AccountNotValidatedException.class)
    public ProblemDetail handleAccountNotValidatedException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AccountNotValidatedException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsedException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, EmailAlreadyUsedException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(PseudoAlreadyUsedException.class)
    public ProblemDetail handlePseudoAlreadyUsedException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, PseudoAlreadyUsedException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(TokenNotValidException.class)
    public ProblemDetail handleTokenNotValidException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, TokenNotValidException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountLockedException(AccountLockedException exception) {
        long retryAfterSeconds = Math.max(1, Duration.between(Instant.now(), exception.getLockedUntil()).toSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, AccountLockedException.EXCEPTION_MESSAGE));
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ProblemDetail handleInvalidVerificationCodeException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, InvalidVerificationCodeException.EXCEPTION_MESSAGE);
    }

    // 403 and not 401: the caller is logged in, only this confirmation failed. A 401 would log the user out.
    @ExceptionHandler(WrongPasswordException.class)
    public ProblemDetail handleWrongPasswordException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, WrongPasswordException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshTokenException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, InvalidRefreshTokenException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AccountCredentialNotFoundException.class)
    public ProblemDetail handleAccountCredentialNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AccountCredentialNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, AccountNotFoundException.EXCEPTION_MESSAGE);
    }
}
