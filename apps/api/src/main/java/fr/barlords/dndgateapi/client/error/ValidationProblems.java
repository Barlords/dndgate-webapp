package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.client.error.dto.ValidationErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.List;

/** The one shape of a "request is not valid" answer, shared by every path that validates input. */
final class ValidationProblems {

    private ValidationProblems() {
    }

    static ProblemDetail of(List<ValidationErrorResponse> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is not valid");
        problem.setProperty("errors", errors);
        return problem;
    }
}
