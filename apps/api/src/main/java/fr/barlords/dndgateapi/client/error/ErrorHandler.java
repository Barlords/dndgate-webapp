package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPageRequestException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPictureException;
import fr.barlords.dndgateapi.domain.functional.exception.UuidFormatNotValidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import fr.barlords.dndgateapi.domain.functional.exception.MessageRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Errors that do not belong to one feature. Every handler answers a {@link ProblemDetail} (RFC 9457):
 * {@code detail} is the explanation, the other fields are the standard ones.
 * Checked before {@link FallbackErrorHandler}, which catches whatever is left.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ErrorHandler {

    static final String DATA_CONFLICT_MESSAGE = "The request conflicts with the current state of the data";

    private final Clock clock;

    @ExceptionHandler(QuantityLimitExceededException.class)
    public ProblemDetail handleQuantityLimitExceededException(QuantityLimitExceededException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MessageRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleMessageRateLimitException(MessageRateLimitException exception) {
        long retryAfterSeconds = Math.max(1, Duration.between(clock.instant(), exception.getRetryAt()).toSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }

    @ExceptionHandler(InvalidPictureException.class)
    public ProblemDetail handleInvalidPictureException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, InvalidPictureException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceededException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The file is too large");
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    public ProblemDetail handleInvalidPageRequestException(InvalidPageRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UuidFormatNotValidException.class)
    public ProblemDetail handleUuidNotValidException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, UuidFormatNotValidException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(DeletionNotConfirmedException.class)
    public ProblemDetail handleDeletionNotConfirmedException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DeletionNotConfirmedException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        log.warn("The database refused a write", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, DATA_CONFLICT_MESSAGE);
    }

    @ExceptionHandler(EmailNotSendException.class)
    public ProblemDetail handleEmailNotSendException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, EmailNotSendException.EXCEPTION_MESSAGE);
    }

}
