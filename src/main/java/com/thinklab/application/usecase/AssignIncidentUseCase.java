package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssignIncidentRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for naming who works an Incident (BIAN Behavior Qualifier: {@code assignment/update}). */
@Singleton
public class AssignIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignIncidentUseCase.class);

    private final IncidentWorkflow workflow;

    public AssignIncidentUseCase(IncidentWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, AssignIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Assigning Incident ID: {} to {}", id, request.assigneeId());

        return workflow.apply(id, organisationId, role, "assign an incident", incident -> incident.assign(request.assigneeId(), executor));
    }
}
