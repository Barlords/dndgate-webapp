package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureFullException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureInProgressException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotJoinableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.InvitationRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.AdventureNoteNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.HistoricalNoteNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.time.Instant;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdventureErrorHandler {

    @ExceptionHandler(AdventureNotFoundException.class)
    public ProblemDetail handleAdventureNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, AdventureNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(PlayerNotInAdventureException.class)
    public ProblemDetail handlePlayerNotInAdventureException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, PlayerNotInAdventureException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureInProgressException.class)
    public ProblemDetail handleAdventureInProgressException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AdventureInProgressException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureConcurrentModificationException.class)
    public ProblemDetail handleAdventureConcurrentModificationException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AdventureConcurrentModificationException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureFullException.class)
    public ProblemDetail handleAdventureFullException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AdventureFullException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureNotJoinableException.class)
    public ProblemDetail handleAdventureNotJoinableException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AdventureNotJoinableException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(PlayersAlreadyInAdventureException.class)
    public ProblemDetail handlePlayersAlreadyInAdventureException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, PlayersAlreadyInAdventureException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(InvitationRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleInvitationRateLimitException(InvitationRateLimitException exception) {
        long retryAfterSeconds = Math.max(1, Duration.between(Instant.now(), exception.getRetryAt()).toSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }

    @ExceptionHandler(AdventureAccessDeniedException.class)
    public ProblemDetail handleAdventureAccessDeniedException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, AdventureAccessDeniedException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureOwnerException.class)
    public ProblemDetail handleAdventureOwnerException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, AdventureOwnerException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureNoteNotFoundException.class)
    public ProblemDetail handleAdventureNoteNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, AdventureNoteNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(HistoricalNoteNotFoundException.class)
    public ProblemDetail handleHistoricalNoteNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, HistoricalNoteNotFoundException.EXCEPTION_MESSAGE);
    }

}
