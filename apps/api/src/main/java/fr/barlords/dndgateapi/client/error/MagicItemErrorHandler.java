package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.magicitem.MagicItemOwnerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MagicItemErrorHandler {

    @ExceptionHandler(MagicItemNotFoundException.class)
    public ProblemDetail handleMagicItemNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, MagicItemNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(MagicItemOwnerException.class)
    public ProblemDetail handleMagicItemOwnerException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, MagicItemOwnerException.EXCEPTION_MESSAGE);
    }

}
