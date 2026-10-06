package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.mapper.IncidentMapper;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for opening a new Incident (BIAN Behavior Qualifier: {@code initiate}).
 *
 * <p>Self-service scoping (ADR-031): a caller authenticated as {@code REQUESTER} can only file on their own behalf, so
 * {@code requesterId} is forced to the token-derived {@code X-Executor} whatever the body says. Any other role must supply
 * {@code requesterId} (staff filing for a user). The priority is derived from impact and urgency (ADR-030) and the SLA due dates come
 * from the configured targets of that priority (ADR-032).
 */
@Singleton
public class InitiateIncidentUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateIncidentUseCase.class);

    private final HashServicePort hashServicePort;
    private final IncidentRepository incidentRepository;
    private final IncidentSlaProperties slaProperties;

    public InitiateIncidentUseCase(HashServicePort hashServicePort, IncidentRepository incidentRepository, IncidentSlaProperties slaProperties) {
        this.hashServicePort = hashServicePort;
        this.incidentRepository = incidentRepository;
        this.slaProperties = slaProperties;
    }

    public Mono<IncidentResponse> execute(UUID organisationId, InitiateIncidentRequest request, String executor, String role) {
        log.info("[USE CASE] Opening an Incident for organisation: {}", organisationId);

        String key = idempotencyKey(request, role);
        Mono<Incident> opened = Mono.defer(() -> Mono.fromCallable(() -> resolveRequesterId(request, executor, role))
                .flatMap(requesterId -> hashServicePort.generateSovereignId("incident-creation")
                        .map(sovereignId -> IncidentMapper.toDomain(request, sovereignId, organisationId, requesterId,
                                slaProperties.targetsFor(Priority.of(request.impact(), request.urgency())), executor)))
                .flatMap(incident -> key == null ? incidentRepository.create(incident) : incidentRepository.createIdempotent(incident, key)));
        Mono<Incident> incident = key == null ? opened : incidentRepository.findByIdempotencyKey(organisationId, key).switchIfEmpty(opened);

        return incident.map(found -> IncidentMapper.toResponse(found, true, Instant.now()));
    }

    /** Only staff may use a key (a REQUESTER could otherwise read back someone else's incident by guessing one); a blank key is no key. */
    private static String idempotencyKey(InitiateIncidentRequest request, String role) {
        boolean usable = !IncidentWorkflow.REQUESTER_ROLE.equals(role) && request.idempotencyKey() != null && !request.idempotencyKey().isBlank();
        return usable ? request.idempotencyKey().trim() : null;
    }

    private static UUID resolveRequesterId(InitiateIncidentRequest request, String executor, String role) {
        if (IncidentWorkflow.REQUESTER_ROLE.equals(role)) {
            return UUID.fromString(executor);
        }
        if (request.requesterId() == null) {
            throw new IllegalArgumentException("requesterId is required when staff open an Incident on someone else's behalf.");
        }
        return request.requesterId();
    }
}
