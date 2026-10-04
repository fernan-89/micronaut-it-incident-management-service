package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssignIncidentRequest;
import com.thinklab.application.dto.request.HoldIncidentRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.request.ReopenIncidentRequest;
import com.thinklab.application.dto.request.ResolveIncidentRequest;
import com.thinklab.application.dto.request.UpdateIncidentRequest;
import com.thinklab.domain.exception.IncidentAccessDeniedException;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.exception.InvalidIncidentStatusException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The incident use cases: who may do what (ADR-031), the SLA targets they hand the aggregate (ADR-032) and what they persist. */
@ExtendWith(MockitoExtension.class)
class IncidentUseCaseTest {

    private static final String EXECUTOR = "op-1";
    private static final String STAFF = "OPERATOR";
    private static final String REQUESTER = "REQUESTER";

    @Mock private HashServicePort hashServicePort;
    @Mock private IncidentRepository incidentRepository;

    private final IncidentSlaProperties sla = new IncidentSlaProperties();
    private final UUID organisationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private IncidentWorkflow workflow;
    private Incident incident;

    @BeforeEach
    void setUp() {
        workflow = new IncidentWorkflow(incidentRepository);
        incident = Incident.createNew(UUID.randomUUID(), organisationId, requesterId, "Switch down", "No link", Impact.MEDIUM, Urgency.MEDIUM,
                null, null, sla.targetsFor(Priority.P3), EXECUTOR);
    }

    private void found() {
        when(incidentRepository.findById(incident.getId(), organisationId)).thenReturn(Mono.just(incident));
    }

    private void saves() {
        when(incidentRepository.save(any(), any(), any())).thenReturn(Mono.empty());
    }

    // ------------------------------------------------------------ SLA configuration

    @Test
    @DisplayName("the SLA targets default per priority and a deployment overrides only what it states")
    void slaProperties() {
        assertEquals(Duration.ofMinutes(15), sla.targetsFor(Priority.P1).response());
        assertEquals(Duration.ofHours(4), sla.targetsFor(Priority.P1).resolution());
        assertEquals(Duration.ofDays(7), sla.targetsFor(Priority.P4).resolution());

        sla.setResponse(Map.of("P1", Duration.ofMinutes(10)));
        sla.setResolution(Map.of("P2", Duration.ofHours(2)));

        SlaTargets p1 = sla.targetsFor(Priority.P1);
        assertEquals(Duration.ofMinutes(10), p1.response());
        assertEquals(Duration.ofHours(4), p1.resolution());
        assertEquals(Duration.ofHours(2), sla.targetsFor(Priority.P2).resolution());
        assertEquals(Duration.ofMinutes(30), sla.targetsFor(Priority.P2).response());
        assertEquals(1, sla.getResponse().size());
        assertEquals(1, sla.getResolution().size());
    }

    // ------------------------------------------------------------ initiate

    @Test
    @DisplayName("staff open an incident for a named requester; the priority and due dates come from impact, urgency and the configured targets")
    void initiateByStaff() {
        var request = new InitiateIncidentRequest("Core down", "Everything", Impact.HIGH, Urgency.HIGH, Set.of(UUID.randomUUID()), null, requesterId);
        when(hashServicePort.generateSovereignId("incident-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(incidentRepository.create(any(Incident.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateIncidentUseCase(hashServicePort, incidentRepository, sla).execute(organisationId, request, EXECUTOR, STAFF))
                .assertNext(response -> {
                    assertEquals("P1", response.priority());
                    assertEquals("NEW", response.status());
                    assertEquals(requesterId, response.requesterId());
                    assertEquals(response.createdAt().plus(Duration.ofMinutes(15)), response.response().dueAt());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("staff must say who the requester is; a REQUESTER is always themselves, whatever the body says")
    void initiateRequesterRules() {
        var withoutRequester = new InitiateIncidentRequest("t", "d", Impact.LOW, Urgency.LOW, null, null, null);
        var spoofed = new InitiateIncidentRequest("t", "d", Impact.LOW, Urgency.LOW, null, null, UUID.randomUUID());
        InitiateIncidentUseCase useCase = new InitiateIncidentUseCase(hashServicePort, incidentRepository, sla);
        when(hashServicePort.generateSovereignId("incident-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(incidentRepository.create(any(Incident.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(organisationId, withoutRequester, EXECUTOR, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(organisationId, spoofed, requesterId.toString(), REQUESTER))
                .assertNext(response -> assertEquals(requesterId, response.requesterId()))
                .verifyComplete();
    }

    // ------------------------------------------------------------ retrieve

    @Test
    @DisplayName("retrieve answers the incident of the tenant; unknown is 404, and a REQUESTER sees only their own, with no internal comments")
    void retrieve() {
        RetrieveIncidentUseCase useCase = new RetrieveIncidentUseCase(incidentRepository);
        incident.addComment(new Incident.Comment(UUID.randomUUID(), EXECUTOR, "Internal", true, null), EXECUTOR);
        found();

        StepVerifier.create(useCase.execute(incident.getId(), organisationId, EXECUTOR, STAFF))
                .assertNext(response -> assertEquals(1, response.comments().size()))
                .verifyComplete();
        StepVerifier.create(useCase.execute(incident.getId(), organisationId, requesterId.toString(), REQUESTER))
                .assertNext(response -> assertTrue(response.comments().isEmpty()))
                .verifyComplete();
        StepVerifier.create(useCase.execute(incident.getId(), organisationId, UUID.randomUUID().toString(), REQUESTER))
                .expectError(IncidentNotFoundException.class).verify();

        UUID unknown = UUID.randomUUID();
        when(incidentRepository.findById(unknown, organisationId)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(unknown, organisationId, EXECUTOR, STAFF)).expectError(IncidentNotFoundException.class).verify();
    }

    @Test
    @DisplayName("the collection passes the filters on, and narrows a REQUESTER to their own incidents")
    void retrieveAll() {
        RetrieveIncidentsUseCase useCase = new RetrieveIncidentsUseCase(incidentRepository);
        UUID assignee = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        when(incidentRepository.findAll(eq(organisationId), any())).thenReturn(Flux.just(incident));

        StepVerifier.create(useCase.execute(organisationId, IncidentStatus.NEW, Priority.P3, assignee, asset, true, EXECUTOR, STAFF))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(organisationId, null, null, null, null, false, requesterId.toString(), REQUESTER))
                .expectNextCount(1).verifyComplete();

        ArgumentCaptor<IncidentRepository.Filter> filter = ArgumentCaptor.forClass(IncidentRepository.Filter.class);
        verify(incidentRepository, org.mockito.Mockito.times(2)).findAll(eq(organisationId), filter.capture());
        assertEquals(new IncidentRepository.Filter(IncidentStatus.NEW, Priority.P3, assignee, asset, null, true), filter.getAllValues().get(0));
        assertEquals(new IncidentRepository.Filter(null, null, null, null, requesterId, false), filter.getAllValues().get(1));
    }

    // ------------------------------------------------------------ staff actions

    @Test
    @DisplayName("a REQUESTER cannot do any staff action: refused before anything is read or written")
    void requesterIsRefused() {
        StepVerifier.create(new AssignIncidentUseCase(workflow).execute(incident.getId(), organisationId, new AssignIncidentRequest(UUID.randomUUID()), EXECUTOR, REQUESTER))
                .expectError(IncidentAccessDeniedException.class).verify();
        StepVerifier.create(new ControlIncidentUseCase(workflow).execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.CANCEL, EXECUTOR, REQUESTER))
                .expectErrorSatisfies(error -> assertEquals("A requester cannot cancel an incident: that is a staff action.", error.getMessage())).verify();
        verifyNoInteractions(incidentRepository);
    }

    @Test
    @DisplayName("an unknown incident of the tenant is 404 for every staff action")
    void unknownIncident() {
        UUID unknown = UUID.randomUUID();
        when(incidentRepository.findById(unknown, organisationId)).thenReturn(Mono.empty());

        StepVerifier.create(new AssignIncidentUseCase(workflow).execute(unknown, organisationId, new AssignIncidentRequest(UUID.randomUUID()), EXECUTOR, STAFF))
                .expectError(IncidentNotFoundException.class).verify();
        verify(incidentRepository, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("update hands the aggregate the targets of the priority the new impact and urgency lead to, then saves with the status it was loaded in")
    void update() {
        found();
        saves();

        StepVerifier.create(new UpdateIncidentUseCase(workflow, sla).execute(incident.getId(), organisationId,
                new UpdateIncidentRequest("Core down", "All", Impact.HIGH, Urgency.HIGH, null, null), EXECUTOR, STAFF)).verifyComplete();

        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(incident.getCreatedAt().plus(Duration.ofMinutes(15)), incident.getResponseDueAt());
        verify(incidentRepository).save(eq(incident), eq(IncidentStatus.NEW), any());
    }

    @Test
    @DisplayName("assign, acknowledge, start, hold, resume, resolve, close and reopen go through the same guarded save")
    void lifecycle() {
        found();
        saves();
        UUID assignee = UUID.randomUUID();
        var control = new ControlIncidentUseCase(workflow);

        StepVerifier.create(new AssignIncidentUseCase(workflow).execute(incident.getId(), organisationId, new AssignIncidentRequest(assignee), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(control.execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.ACKNOWLEDGE, EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(control.execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.START, EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(new HoldIncidentUseCase(workflow).execute(incident.getId(), organisationId, new HoldIncidentRequest("vendor"), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(control.execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.RESUME, EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(new ResolveIncidentUseCase(workflow).execute(incident.getId(), organisationId, new ResolveIncidentRequest("FIXED", "done"), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(new ReopenIncidentUseCase(workflow).execute(incident.getId(), organisationId, new ReopenIncidentRequest("again"), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(new ResolveIncidentUseCase(workflow).execute(incident.getId(), organisationId, new ResolveIncidentRequest("FIXED", "done"), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(control.execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.CLOSE, EXECUTOR, STAFF)).verifyComplete();

        Incident another = Incident.createNew(UUID.randomUUID(), organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, sla.targetsFor(Priority.P4), EXECUTOR);
        when(incidentRepository.findById(another.getId(), organisationId)).thenReturn(Mono.just(another));
        StepVerifier.create(control.execute(another.getId(), organisationId, ControlIncidentUseCase.Action.CANCEL, EXECUTOR, STAFF)).verifyComplete();
        assertEquals(IncidentStatus.CANCELLED, another.getStatus());

        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertEquals(assignee, incident.getAssigneeId());
        assertEquals(1, incident.getReopenCount());
        verify(incidentRepository, org.mockito.Mockito.times(9)).save(eq(incident), any(), any());
        verify(incidentRepository).save(eq(another), eq(IncidentStatus.NEW), any());
    }

    @Test
    @DisplayName("an illegal transition is refused by the aggregate and nothing is saved")
    void illegalTransition() {
        found();

        StepVerifier.create(new ControlIncidentUseCase(workflow).execute(incident.getId(), organisationId, ControlIncidentUseCase.Action.START, EXECUTOR, STAFF))
                .expectError(InvalidIncidentStatusException.class).verify();
        verify(incidentRepository, never()).save(any(), any(), any());
    }

    // ------------------------------------------------------------ comments

    @Test
    @DisplayName("staff can write internal notes; a REQUESTER cannot, and can comment only on their own incident")
    void comments() {
        InitiateCommentUseCase useCase = new InitiateCommentUseCase(hashServicePort, incidentRepository);
        found();
        when(hashServicePort.generateSovereignId("incident-comment-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(incidentRepository.addComment(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(incident.getId(), organisationId, new InitiateCommentRequest("secret", true), EXECUTOR, STAFF)).verifyComplete();
        StepVerifier.create(useCase.execute(incident.getId(), organisationId, new InitiateCommentRequest("still failing", true), requesterId.toString(), REQUESTER)).verifyComplete();
        StepVerifier.create(useCase.execute(incident.getId(), organisationId, new InitiateCommentRequest("hi", false), UUID.randomUUID().toString(), REQUESTER))
                .expectError(IncidentNotFoundException.class).verify();

        ArgumentCaptor<Incident.Comment> comment = ArgumentCaptor.forClass(Incident.Comment.class);
        verify(incidentRepository, org.mockito.Mockito.times(2)).addComment(eq(incident.getId()), eq(organisationId), comment.capture(), any());
        assertTrue(comment.getAllValues().get(0).internal());
        assertFalse(comment.getAllValues().get(1).internal());
    }

    @Test
    @DisplayName("an unknown incident cannot be commented on")
    void commentOnUnknown() {
        UUID unknown = UUID.randomUUID();
        when(incidentRepository.findById(unknown, organisationId)).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateCommentUseCase(hashServicePort, incidentRepository).execute(unknown, organisationId, new InitiateCommentRequest("hi", false), EXECUTOR, STAFF))
                .expectError(IncidentNotFoundException.class).verify();
    }

    // ------------------------------------------------------------ audit log

    @Test
    @DisplayName("the audit trail is for staff: a REQUESTER is refused, unknown is 404, and staff read it in order")
    void auditLog() {
        RetrieveIncidentAuditLogUseCase useCase = new RetrieveIncidentAuditLogUseCase(incidentRepository);
        incident.acknowledge(EXECUTOR);
        found();

        StepVerifier.create(useCase.execute(incident.getId(), organisationId, STAFF))
                .assertNext(entries -> {
                    assertEquals(2, entries.size());
                    assertEquals("INITIATED", entries.get(0).action());
                    assertNull(entries.get(0).fromStatus());
                })
                .verifyComplete();
        StepVerifier.create(useCase.execute(incident.getId(), organisationId, REQUESTER)).expectError(IncidentAccessDeniedException.class).verify();

        UUID unknown = UUID.randomUUID();
        when(incidentRepository.findById(unknown, organisationId)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(unknown, organisationId, STAFF)).expectError(IncidentNotFoundException.class).verify();
    }
}
