package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.planning.AvailabilityNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AvailabilityErrorHandler {
    @ExceptionHandler(AvailabilityNotFoundException.class)
    public ProblemDetail handlePlanningNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, AvailabilityNotFoundException.EXCEPTION_MESSAGE);
    }
}
