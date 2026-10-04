package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.HoldIncidentRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for putting an Incident on hold (BIAN Behavior Qualifier: {@code control/hold}); the reason is mandatory. */
@Singleton
public class HoldIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(HoldIncidentUseCase.class);

    private final IncidentWorkflow workflow;

    public HoldIncidentUseCase(IncidentWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, HoldIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Putting Incident ID: {} on hold", id);

        return workflow.apply(id, organisationId, role, "put an incident on hold", incident -> incident.hold(request.reason(), executor));
    }
}
