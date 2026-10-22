package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanningErrorHandler {

    @ExceptionHandler(PlanningAlreadyExistsException.class)
    public ProblemDetail handlePlanningAlreadyExistsException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, PlanningAlreadyExistsException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(PlanningNotFoundException.class)
    public ProblemDetail handlePlanningNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, PlanningNotFoundException.EXCEPTION_MESSAGE);
    }

}
