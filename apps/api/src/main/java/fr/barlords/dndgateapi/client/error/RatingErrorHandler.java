package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.rating.NotPlayedTogetherException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.SelfRatingException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RatingErrorHandler {

    @ExceptionHandler(RatingNotFoundException.class)
    public ProblemDetail handleRatingNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, RatingNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(RatingOwnerException.class)
    public ProblemDetail handleRatingOwnerException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, RatingOwnerException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(NotPlayedTogetherException.class)
    public ProblemDetail handleNotPlayedTogetherException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, NotPlayedTogetherException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(SelfRatingException.class)
    public ProblemDetail handleSelfRatingException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, SelfRatingException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(RatingAlreadyExistsException.class)
    public ProblemDetail handleRatingAlreadyExistsException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, RatingAlreadyExistsException.EXCEPTION_MESSAGE);
    }

}
