package com.thinklab.application.usecase;

import com.thinklab.domain.exception.IncidentAccessDeniedException;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/**
 * The common shape of every staff action on an incident: refuse a REQUESTER (ADR-031), load the incident of the tenant (another
 * tenant's is not found), let the aggregate perform the operation and persist what it did, atomically with its audit entry and only
 * if nobody else moved the incident in between (a lost race is a 409 the caller can retry).
 */
@Singleton
public class IncidentWorkflow {

    static final String REQUESTER_ROLE = "REQUESTER";

    private final IncidentRepository incidentRepository;

    public IncidentWorkflow(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /** @param operation what the caller tried to do, in words, for the refusal message */
    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<Incident, IncidentAuditEntry> action) {
        if (REQUESTER_ROLE.equals(role)) {
            return Mono.error(new IncidentAccessDeniedException(operation));
        }
        return incidentRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new IncidentNotFoundException(id)))
                .flatMap(incident -> {
                    var statusBefore = incident.getStatus();
                    IncidentAuditEntry entry = action.apply(incident);
                    return incidentRepository.save(incident, statusBefore, entry);
                });
    }
}
