package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AlreadyMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimAlreadyOpenException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterInvitationNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterSeatNotVacantException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToClaimMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToVoteException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdventureMasterErrorHandler {

    @ExceptionHandler(MasterClaimNotFoundException.class)
    public ProblemDetail handleMasterClaimNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, MasterClaimNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(MasterInvitationNotFoundException.class)
    public ProblemDetail handleMasterInvitationNotFoundException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, MasterInvitationNotFoundException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(NotEligibleToClaimMasterException.class)
    public ProblemDetail handleNotEligibleToClaimMasterException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, NotEligibleToClaimMasterException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(NotEligibleToVoteException.class)
    public ProblemDetail handleNotEligibleToVoteException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, NotEligibleToVoteException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(MasterSeatNotVacantException.class)
    public ProblemDetail handleMasterSeatNotVacantException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, MasterSeatNotVacantException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(MasterClaimAlreadyOpenException.class)
    public ProblemDetail handleMasterClaimAlreadyOpenException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, MasterClaimAlreadyOpenException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AdventureNotTransferableException.class)
    public ProblemDetail handleAdventureNotTransferableException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AdventureNotTransferableException.EXCEPTION_MESSAGE);
    }

    @ExceptionHandler(AlreadyMasterException.class)
    public ProblemDetail handleAlreadyMasterException() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, AlreadyMasterException.EXCEPTION_MESSAGE);
    }
}
