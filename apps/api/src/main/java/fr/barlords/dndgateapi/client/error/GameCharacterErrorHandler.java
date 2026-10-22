package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GameCharacterErrorHandler {

    @ExceptionHandler(GameCharacterNotFoundException.class)
    public ProblemDetail handleGameCharacterNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, GameCharacterNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(GameCharacterOwnerException.class)
    public ProblemDetail handleGameCharacterOwnerException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, GameCharacterOwnerException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(InvalidCharacterFilterException.class)
    public ProblemDetail handleInvalidCharacterFilterException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, InvalidCharacterFilterException.EXCEPTION_MESSAGE);
    }

}
