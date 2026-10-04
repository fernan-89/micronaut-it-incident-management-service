package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.IncidentAuditEntryResponse;
import com.thinklab.application.mapper.IncidentMapper;
import com.thinklab.domain.exception.IncidentAccessDeniedException;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the immutable forensic ledger of an Incident (BIAN Behavior Qualifier: {@code audit-log/retrieve}); a staff action. */
@Singleton
public class RetrieveIncidentAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveIncidentAuditLogUseCase.class);

    private final IncidentRepository incidentRepository;

    public RetrieveIncidentAuditLogUseCase(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Mono<List<IncidentAuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of Incident ID: {}", id);

        if (IncidentWorkflow.REQUESTER_ROLE.equals(role)) {
            return Mono.error(new IncidentAccessDeniedException("read the audit trail of an incident"));
        }
        return incidentRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new IncidentNotFoundException(id)))
                .map(incident -> incident.getAuditTrail().stream().map(IncidentMapper::toResponse).collect(Collectors.toList()));
    }
}
