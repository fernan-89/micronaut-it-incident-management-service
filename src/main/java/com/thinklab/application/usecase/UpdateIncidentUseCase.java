package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateIncidentRequest;
import com.thinklab.domain.model.Incident.Priority;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for updating an Incident (BIAN Behavior Qualifier: {@code update}). The SLA targets of the priority the new impact and urgency
 * lead to are looked up here and handed to the aggregate, which restarts the due dates only if the priority actually changed.
 */
@Singleton
public class UpdateIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateIncidentUseCase.class);

    private final IncidentWorkflow workflow;
    private final IncidentSlaProperties slaProperties;

    public UpdateIncidentUseCase(IncidentWorkflow workflow, IncidentSlaProperties slaProperties) {
        this.workflow = workflow;
        this.slaProperties = slaProperties;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Updating Incident ID: {}", id);

        return workflow.apply(id, organisationId, role, "update an incident",
                incident -> incident.updateDetails(request.title(), request.description(), request.impact(), request.urgency(),
                        request.affectedAssetIds(), request.relatedChangeIds(),
                        slaProperties.targetsFor(Priority.of(request.impact(), request.urgency())), executor));
    }
}
