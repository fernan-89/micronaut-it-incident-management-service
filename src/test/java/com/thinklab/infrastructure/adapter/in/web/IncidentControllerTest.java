package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.AssignIncidentRequest;
import com.thinklab.application.dto.request.HoldIncidentRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.request.ReopenIncidentRequest;
import com.thinklab.application.dto.request.ResolveIncidentRequest;
import com.thinklab.application.dto.request.UpdateIncidentRequest;
import com.thinklab.application.dto.response.IncidentAuditEntryResponse;
import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.usecase.AssignIncidentUseCase;
import com.thinklab.application.usecase.ControlIncidentUseCase;
import com.thinklab.application.usecase.HoldIncidentUseCase;
import com.thinklab.application.usecase.InitiateCommentUseCase;
import com.thinklab.application.usecase.InitiateIncidentUseCase;
import com.thinklab.application.usecase.ReopenIncidentUseCase;
import com.thinklab.application.usecase.ResolveIncidentUseCase;
import com.thinklab.application.usecase.RetrieveIncidentAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveIncidentUseCase;
import com.thinklab.application.usecase.RetrieveIncidentsUseCase;
import com.thinklab.application.usecase.UpdateIncidentUseCase;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.Urgency;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    private static final String EXECUTOR = "op-1";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();

    @Mock private InitiateIncidentUseCase initiateIncidentUseCase;
    @Mock private RetrieveIncidentUseCase retrieveIncidentUseCase;
    @Mock private RetrieveIncidentsUseCase retrieveIncidentsUseCase;
    @Mock private UpdateIncidentUseCase updateIncidentUseCase;
    @Mock private AssignIncidentUseCase assignIncidentUseCase;
    @Mock private ControlIncidentUseCase controlIncidentUseCase;
    @Mock private HoldIncidentUseCase holdIncidentUseCase;
    @Mock private ResolveIncidentUseCase resolveIncidentUseCase;
    @Mock private ReopenIncidentUseCase reopenIncidentUseCase;
    @Mock private InitiateCommentUseCase initiateCommentUseCase;
    @Mock private RetrieveIncidentAuditLogUseCase retrieveIncidentAuditLogUseCase;

    private IncidentController controller;

    @BeforeEach
    void setUp() {
        controller = new IncidentController(initiateIncidentUseCase, retrieveIncidentUseCase, retrieveIncidentsUseCase, updateIncidentUseCase,
                assignIncidentUseCase, controlIncidentUseCase, holdIncidentUseCase, resolveIncidentUseCase, reopenIncidentUseCase,
                initiateCommentUseCase, retrieveIncidentAuditLogUseCase);
    }

    private IncidentResponse sample() {
        return new IncidentResponse(id, tenant, UUID.randomUUID(), "t", "d", "LOW", "LOW", "P4", "NEW", null, Set.of(), Set.of(), null, null, null, 0,
                null, null, null, null, List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate answers 201 Created with the tenant, executor and role passed on")
    void initiate() {
        var request = new InitiateIncidentRequest("t", "d", Impact.LOW, Urgency.LOW, null, null, UUID.randomUUID());
        when(initiateIncidentUseCase.execute(tenant, request, EXECUTOR, "OPERATOR")).thenReturn(Mono.just(sample()));

        var response = controller.initiate(tenantHeader, EXECUTOR, "OPERATOR", request).block();

        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Test
    @DisplayName("retrieve by id is tenant-scoped and answers 200")
    void retrieveById() {
        when(retrieveIncidentUseCase.execute(id, tenant, EXECUTOR, "REQUESTER")).thenReturn(Mono.just(sample()));

        assertEquals(HttpStatus.OK, controller.retrieveById(id, tenantHeader, EXECUTOR, "REQUESTER").block().getStatus());
    }

    @Test
    @DisplayName("retrieve (collection) passes every filter on")
    void retrieveAll() {
        UUID assignee = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        when(retrieveIncidentsUseCase.execute(tenant, IncidentStatus.NEW, Priority.P1, assignee, asset, true, EXECUTOR, null)).thenReturn(Flux.just(sample()));

        var result = controller.retrieveAll(tenantHeader, EXECUTOR, null, IncidentStatus.NEW, Priority.P1, assignee, asset, true).block();

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("update, assignment, hold, resolve and reopen answer 204 No Content and carry the tenant and role")
    void bodyActions() {
        var update = new UpdateIncidentRequest("t", "d", Impact.LOW, Urgency.LOW, null, null);
        var assign = new AssignIncidentRequest(UUID.randomUUID());
        var hold = new HoldIncidentRequest("vendor");
        var resolve = new ResolveIncidentRequest("FIXED", "done");
        var reopen = new ReopenIncidentRequest("again");
        when(updateIncidentUseCase.execute(id, tenant, update, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());
        when(assignIncidentUseCase.execute(id, tenant, assign, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());
        when(holdIncidentUseCase.execute(id, tenant, hold, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());
        when(resolveIncidentUseCase.execute(id, tenant, resolve, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());
        when(reopenIncidentUseCase.execute(id, tenant, reopen, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.update(id, tenantHeader, EXECUTOR, "OPERATOR", update).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.assign(id, tenantHeader, EXECUTOR, "OPERATOR", assign).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlHold(id, tenantHeader, EXECUTOR, "OPERATOR", hold).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlResolve(id, tenantHeader, EXECUTOR, "OPERATOR", resolve).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlReopen(id, tenantHeader, EXECUTOR, "OPERATOR", reopen).block().getStatus());
    }

    @Test
    @DisplayName("the status-only transitions each pick their own action")
    void controlActions() {
        when(controlIncidentUseCase.execute(eq(id), eq(tenant), any(), eq(EXECUTOR), eq("OPERATOR"))).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlAcknowledge(id, tenantHeader, EXECUTOR, "OPERATOR").block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlStart(id, tenantHeader, EXECUTOR, "OPERATOR").block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlResume(id, tenantHeader, EXECUTOR, "OPERATOR").block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlClose(id, tenantHeader, EXECUTOR, "OPERATOR").block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlCancel(id, tenantHeader, EXECUTOR, "OPERATOR").block().getStatus());

        for (var action : ControlIncidentUseCase.Action.values()) {
            verify(controlIncidentUseCase).execute(id, tenant, action, EXECUTOR, "OPERATOR");
        }
    }

    @Test
    @DisplayName("comment/initiate answers 201 and passes the role (a REQUESTER cannot write internal notes)")
    void comment() {
        var request = new InitiateCommentRequest("hello", false);
        when(initiateCommentUseCase.execute(id, tenant, request, EXECUTOR, "REQUESTER")).thenReturn(Mono.empty());

        assertEquals(HttpStatus.CREATED, controller.initiateComment(id, tenantHeader, EXECUTOR, "REQUESTER", request).block().getStatus());
    }

    @Test
    @DisplayName("audit-log/retrieve passes the tenant and role")
    void auditLog() {
        var entry = new IncidentAuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "NEW", "d");
        when(retrieveIncidentAuditLogUseCase.execute(id, tenant, "OPERATOR")).thenReturn(Mono.just(List.of(entry)));

        assertEquals(1, controller.retrieveAuditLog(id, tenantHeader, "OPERATOR").block().size());
    }

    @Test
    @DisplayName("a malformed tenant id is an IllegalArgumentException inside the reactive chain, not a thrown 500")
    void malformedTenant() {
        var mono = controller.retrieveById(id, "not-a-uuid", EXECUTOR, null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, mono::block);
    }
}
