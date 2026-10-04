package com.thinklab.application.usecase;

import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for the status-only transitions of an Incident (BIAN Behavior Qualifiers {@code control/acknowledge}, {@code start},
 * {@code resume}, {@code close} and {@code cancel}). The ones that carry data of their own - hold, resolve, reopen - have their own use cases.
 */
@Singleton
public class ControlIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlIncidentUseCase.class);

    /** One status-only transition: its name, for the refusal message, and what the aggregate does. */
    public enum Action {
        ACKNOWLEDGE("acknowledge an incident") {
            @Override IncidentAuditEntry apply(Incident incident, String executor) { return incident.acknowledge(executor); }
        },
        START("start work on an incident") {
            @Override IncidentAuditEntry apply(Incident incident, String executor) { return incident.start(executor); }
        },
        RESUME("resume an incident") {
            @Override IncidentAuditEntry apply(Incident incident, String executor) { return incident.resume(executor); }
        },
        CLOSE("close an incident") {
            @Override IncidentAuditEntry apply(Incident incident, String executor) { return incident.close(executor); }
        },
        CANCEL("cancel an incident") {
            @Override IncidentAuditEntry apply(Incident incident, String executor) { return incident.cancel(executor); }
        };

        private final String operation;

        Action(String operation) {
            this.operation = operation;
        }

        abstract IncidentAuditEntry apply(Incident incident, String executor);
    }

    private final IncidentWorkflow workflow;

    public ControlIncidentUseCase(IncidentWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on Incident ID: {}", action, id);

        return workflow.apply(id, organisationId, role, action.operation, incident -> action.apply(incident, executor));
    }
}
