package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ResolveIncidentRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for resolving an Incident (BIAN Behavior Qualifier: {@code control/resolve}); a resolution code and notes are mandatory. */
@Singleton
public class ResolveIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResolveIncidentUseCase.class);

    private final IncidentWorkflow workflow;

    public ResolveIncidentUseCase(IncidentWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, ResolveIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Resolving Incident ID: {}", id);

        return workflow.apply(id, organisationId, role, "resolve an incident", incident -> incident.resolve(request.resolutionCode(), request.notes(), executor));
    }
}
