package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPageRequestException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPictureException;
import fr.barlords.dndgateapi.domain.functional.exception.UuidFormatNotValidException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Errors that do not belong to one feature. Every handler answers a {@link ProblemDetail} (RFC 9457):
 * {@code detail} is the explanation, the other fields are the standard ones.
 * Checked before {@link FallbackErrorHandler}, which catches whatever is left.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorHandler {

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

    @ExceptionHandler(DataNotSaveException.class)
    public ProblemDetail handleDataNotSaveException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, DataNotSaveException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(EmailNotSendException.class)
    public ProblemDetail handleEmailNotSendException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, EmailNotSendException.EXCEPTION_MESSAGE);
    }

}
