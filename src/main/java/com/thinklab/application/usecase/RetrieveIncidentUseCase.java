package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.mapper.IncidentMapper;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for retrieving a single Incident (BIAN Behavior Qualifier: {@code retrieve}). The lookup is tenant-scoped, and a REQUESTER
 * viewing someone else's incident gets the same 404 as a missing id: a denied resource is indistinguishable from an unknown one.
 */
@Singleton
public class RetrieveIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveIncidentUseCase.class);

    private final IncidentRepository incidentRepository;

    public RetrieveIncidentUseCase(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Mono<IncidentResponse> execute(UUID id, UUID organisationId, String executor, String role) {
        log.info("[USE CASE] Retrieving Incident by ID: {}", id);

        boolean requester = IncidentWorkflow.REQUESTER_ROLE.equals(role);
        return incidentRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new IncidentNotFoundException(id)))
                .flatMap(incident -> authorize(incident, id, executor, requester))
                .map(incident -> IncidentMapper.toResponse(incident, !requester, Instant.now()));
    }

    private static Mono<Incident> authorize(Incident incident, UUID id, String executor, boolean requester) {
        if (requester && !incident.getRequesterId().equals(UUID.fromString(executor))) {
            return Mono.error(new IncidentNotFoundException(id));
        }
        return Mono.just(incident);
    }
}
