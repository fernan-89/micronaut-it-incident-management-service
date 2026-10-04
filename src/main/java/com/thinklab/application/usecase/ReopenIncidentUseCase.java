package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReopenIncidentRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reopening a resolved Incident (BIAN Behavior Qualifier: {@code control/reopen}); the reason is mandatory. */
@Singleton
public class ReopenIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReopenIncidentUseCase.class);

    private final IncidentWorkflow workflow;

    public ReopenIncidentUseCase(IncidentWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, ReopenIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Reopening Incident ID: {}", id);

        return workflow.apply(id, organisationId, role, "reopen an incident", incident -> incident.reopen(request.reason(), executor));
    }
}
