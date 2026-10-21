package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.server.audit.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes the audit trace to its own logger ({@code AUDIT}), so that it can be kept longer than the rest of the logs
 * or sent elsewhere. The time is the one of the log line; the request identifier (correlationId) is added by the logging setup.
 * <p>
 * Inside a transaction, the line waits for the commit: a deletion that is rolled back, or not yet accepted by
 * the database, leaves no line. Outside a transaction the deletion is already final when this is called.
 */
@Component
public class AuditAdapter implements AuditSpi {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void deletion(String subject, UUID actorId, UUID subjectId) {
        AfterCommit.run(() -> write(subject, actorId, subjectId));
    }

    private void write(String subject, UUID actorId, UUID subjectId) {
        AUDIT.atInfo()
                .addKeyValue("event", "deletion")
                .addKeyValue("subject", subject)
                .addKeyValue("actorId", actorId)
                .addKeyValue("subjectId", subjectId)
                .log("Deletion confirmed: {} {} by account {}", subject, subjectId, actorId);
    }

}
