package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookExportException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookImportException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookOwnerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpellBookErrorHandler {

    @ExceptionHandler(SpellBookNotFoundException.class)
    public ProblemDetail handleSpellBookNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, SpellBookNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(SpellBookImportException.class)
    public ProblemDetail handleSpellBookImportException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, SpellBookImportException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(SpellBookExportException.class)
    public ProblemDetail handleSpellBookExportException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, SpellBookExportException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(InvalidImportFileException.class)
    public ProblemDetail handleInvalidImportFileException(InvalidImportFileException ex) {
        return ValidationProblems.of(ex.getErrors());
    }

    @ExceptionHandler(SpellBookOwnerException.class)
    public ProblemDetail handleSpellBookOwnerException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, SpellBookOwnerException.EXCEPTION_MESSAGE);
    }

}
