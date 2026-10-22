package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.client.error.dto.ValidationErrorResponse;
import lombok.Getter;

import java.util.List;

/** An imported file that breaks the constraints of its DTO; answered as a 400 with the list of errors. */
@Getter
public class InvalidImportFileException extends RuntimeException {

    private final transient List<ValidationErrorResponse> errors;

    public InvalidImportFileException(List<ValidationErrorResponse> errors) {
        super("The imported file is not valid");
        this.errors = errors;
    }
}
