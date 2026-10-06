package com.thinklab.domain.repository;

import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for Incident persistence operations (IT Incident Management Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002). {@link #create(Incident)} is the only whole-document write; every change is
 * one atomic update that also appends its forensic {@link IncidentAuditEntry}, so state and ledger can never diverge. There is no
 * {@code deleteById}: {@code control/cancel} and {@code control/close} reach a terminal status through {@link #save}.
 *
 * <p>Every lookup is tenant-scoped: another organisation's incident is simply not found.
 */
public interface IncidentRepository {

    Mono<Incident> create(Incident incident);

    Mono<Incident> findById(UUID id, UUID organisationId);

    /** Opens an incident under a key that is unique per organisation: a second call with the same key (even a simultaneous one) gets the first incident back. */
    Mono<Incident> createIdempotent(Incident incident, String idempotencyKey);

    /** The incident a key opened, if any. */
    Mono<Incident> findByIdempotencyKey(UUID organisationId, String idempotencyKey);

    Flux<Incident> findAll(UUID organisationId, Filter filter);

    /**
     * Persists the state the aggregate reached by one domain operation, together with its audit entry. The write only applies while the
     * incident is still in {@code expectedStatus} (the status it had when it was loaded), so two people working the same incident
     * cannot silently overwrite each other: the loser gets {@link com.thinklab.domain.exception.InvalidIncidentStatusException} (409).
     */
    Mono<Void> save(Incident incident, IncidentStatus expectedStatus, IncidentAuditEntry auditEntry);

    Mono<Void> addComment(UUID id, UUID organisationId, Comment comment, IncidentAuditEntry auditEntry);

    /**
     * Optional filters of the collection. {@code requesterId} is how the application layer narrows a REQUESTER to their own incidents;
     * {@code openOnly} leaves out RESOLVED, CLOSED and CANCELLED.
     */
    record Filter(IncidentStatus status, Priority priority, UUID assigneeId, UUID assetId, UUID requesterId, boolean openOnly) {
    }
}
