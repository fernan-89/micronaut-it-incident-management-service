package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.mapper.IncidentMapper;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for the tenant-scoped Incident collection (BIAN Behavior Qualifier: {@code retrieve}). A REQUESTER is always narrowed to their
 * own incidents (a filter in the query, not a post-hoc filter over the whole tenant) and never sees internal comments.
 */
@Singleton
public class RetrieveIncidentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveIncidentsUseCase.class);

    private final IncidentRepository incidentRepository;

    public RetrieveIncidentsUseCase(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Flux<IncidentResponse> execute(UUID organisationId, IncidentStatus status, Priority priority, UUID assigneeId, UUID assetId,
                                          boolean openOnly, String executor, String role) {
        log.info("[USE CASE] Retrieving Incidents for organisation: {} status: {} priority: {} role: {}", organisationId, status, priority, role);

        boolean requester = IncidentWorkflow.REQUESTER_ROLE.equals(role);
        UUID requesterFilter = requester ? UUID.fromString(executor) : null;
        Instant now = Instant.now();
        return incidentRepository.findAll(organisationId, new IncidentRepository.Filter(status, priority, assigneeId, assetId, requesterFilter, openOnly))
                .map(incident -> IncidentMapper.toResponse(incident, !requester, now));
    }
}
