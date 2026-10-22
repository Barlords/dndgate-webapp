package fr.barlords.dndgateapi.client.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Clock;

class ErrorHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ErrorHandler.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
    }

    @Test
    void a_violated_constraint_is_a_409_that_leaks_neither_sql_nor_constraint_name() {
        Throwable cause = new IllegalStateException("duplicate key value violates unique constraint \"planning_adventure_id_key\"");

        ProblemDetail problem = new ErrorHandler(Clock.systemUTC())
                .handleDataIntegrityViolationException(new DataIntegrityViolationException("insert into planning ...", cause));

        Assertions.assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        Assertions.assertFalse(problem.getDetail().isBlank());
        Assertions.assertFalse(problem.getDetail().contains("planning"));
        Assertions.assertFalse(problem.getDetail().contains("insert"));
    }

    @Test
    void a_violated_constraint_is_logged_with_its_cause() {
        Throwable cause = new IllegalStateException("planning_adventure_id_key");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("insert into planning ...", cause);

        new ErrorHandler(Clock.systemUTC()).handleDataIntegrityViolationException(exception);

        Assertions.assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        Assertions.assertEquals(Level.WARN, event.getLevel());
        Assertions.assertNotNull(event.getThrowableProxy());
        Assertions.assertEquals(DataIntegrityViolationException.class.getName(), event.getThrowableProxy().getClassName());
        Assertions.assertNotNull(event.getThrowableProxy().getCause(), "the cause is not lost");
    }

    @Test
    void a_write_made_from_a_stale_read_is_a_409() {
        ProblemDetail problem = new ErrorHandler(Clock.systemUTC())
                .handleConcurrentUpdateException(new ConcurrentUpdateException(new IllegalStateException("version 3 expected")));

        Assertions.assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        Assertions.assertEquals(ConcurrentUpdateException.EXCEPTION_MESSAGE, problem.getDetail());
    }

    @Test
    void a_stale_write_detected_at_commit_is_the_same_409_without_internal_details() {
        ProblemDetail problem = new ErrorHandler(Clock.systemUTC())
                .handleOptimisticLockingFailureException(new ObjectOptimisticLockingFailureException("SpellBookEntity", "an id"));

        Assertions.assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        Assertions.assertEquals(ConcurrentUpdateException.EXCEPTION_MESSAGE, problem.getDetail());
        Assertions.assertEquals(1, appender.list.size(), "logged, as it should have been caught earlier");
    }
}
