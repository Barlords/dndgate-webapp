package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.client.error.dto.ValidationErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * The last advice asked: the errors of the framework itself (unknown route, wrong method, unreadable body...) are
 * answered as problem details by {@link ResponseEntityExceptionHandler}, and anything unexpected becomes a 500
 * that says nothing about the cause (it is logged, not sent).
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackErrorHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();

        return handleExceptionInternal(ex, ValidationProblems.of(errors), headers, status, request);
    }

    /** Security decides what to answer to these: they must reach its filters, not become a 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

}
