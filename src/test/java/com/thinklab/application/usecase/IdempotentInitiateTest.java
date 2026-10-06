package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.IncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Opening an incident under an idempotency key: a repeat answers with the first incident, and only staff can use a key. */
@ExtendWith(MockitoExtension.class)
class IdempotentInitiateTest {

    private static final String EXECUTOR = "system:alerting";
    private static final String STAFF = "OPERATOR";

    @Mock private HashServicePort hashServicePort;
    @Mock private IncidentRepository incidentRepository;

    private final IncidentSlaProperties sla = new IncidentSlaProperties();
    private final UUID organisationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    private InitiateIncidentRequest request(String key) {
        return new InitiateIncidentRequest("Health check down: Intranet", "d", Impact.LOW, Urgency.LOW, null, null, requesterId, key);
    }

    private Incident existing() {
        return Incident.createNew(UUID.randomUUID(), organisationId, requesterId, "Health check down: Intranet", "d", Impact.LOW, Urgency.LOW, null, null,
                sla.targetsFor(Priority.P4), EXECUTOR);
    }

    @Test
    @DisplayName("a key that already opened an incident answers with it: nothing is created and no sovereign id is spent")
    void replay() {
        Incident first = existing();
        when(incidentRepository.findByIdempotencyKey(organisationId, "alert-1")).thenReturn(Mono.just(first));

        StepVerifier.create(new InitiateIncidentUseCase(hashServicePort, incidentRepository, sla).execute(organisationId, request(" alert-1 "), EXECUTOR, STAFF))
                .assertNext(response -> assertEquals(first.getId(), response.id())).verifyComplete();

        verify(incidentRepository, never()).createIdempotent(any(), any());
        verify(incidentRepository, never()).create(any());
    }

    @Test
    @DisplayName("a new key opens the incident under that key, trimmed")
    void firstCall() {
        when(incidentRepository.findByIdempotencyKey(organisationId, "alert-2")).thenReturn(Mono.empty());
        when(hashServicePort.generateSovereignId("incident-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(incidentRepository.createIdempotent(any(Incident.class), eq("alert-2"))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(new InitiateIncidentUseCase(hashServicePort, incidentRepository, sla).execute(organisationId, request("alert-2 "), EXECUTOR, STAFF))
                .assertNext(response -> assertEquals("P4", response.priority())).verifyComplete();
    }

    @Test
    @DisplayName("a blank key is no key, and a REQUESTER's key is ignored: both open a plain incident")
    void noKey() {
        when(hashServicePort.generateSovereignId("incident-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(incidentRepository.create(any(Incident.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        InitiateIncidentUseCase useCase = new InitiateIncidentUseCase(hashServicePort, incidentRepository, sla);

        StepVerifier.create(useCase.execute(organisationId, request("  "), EXECUTOR, STAFF)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(organisationId, request("guess"), requesterId.toString(), "REQUESTER")).expectNextCount(1).verifyComplete();

        verify(incidentRepository, never()).findByIdempotencyKey(any(), any());
    }
}
