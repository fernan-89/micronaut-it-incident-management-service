package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentMapperTest {

    private static final SlaTargets TARGETS = new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8));
    private final UUID organisationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    private Incident incident() {
        var request = new InitiateIncidentRequest("Switch down", "No link", Impact.HIGH, Urgency.HIGH, Set.of(UUID.randomUUID()), null, null, null);
        return IncidentMapper.toDomain(request, UUID.randomUUID(), organisationId, requesterId, TARGETS, "op-1");
    }

    @Test
    @DisplayName("toDomain builds an incident from the request, the sovereign id, the organisation, the requester and the SLA targets")
    void toDomain() {
        Incident incident = incident();

        assertEquals("Switch down", incident.getTitle());
        assertEquals(organisationId, incident.getOrganisationId());
        assertEquals(requesterId, incident.getRequesterId());
        assertEquals("P1", incident.getPriority().name());
    }

    @Test
    @DisplayName("toResponse shows the SLA as pending while the targets run, and breached once they are past")
    void slaWhileRunning() {
        Incident incident = incident();

        IncidentResponse early = IncidentMapper.toResponse(incident, true, incident.getCreatedAt().plusSeconds(60));
        IncidentResponse late = IncidentMapper.toResponse(incident, true, incident.getCreatedAt().plus(Duration.ofHours(9)));

        assertEquals("PENDING", early.response().state());
        assertEquals("PENDING", early.resolution().state());
        assertEquals(incident.getResponseDueAt(), early.response().dueAt());
        assertEquals("BREACHED", late.response().state());
        assertEquals("BREACHED", late.resolution().state());
    }

    @Test
    @DisplayName("once acknowledged and resolved the SLA is judged by when that happened, not by the time of reading")
    void slaOnceDone() {
        Incident incident = incident();
        incident.acknowledge("op-1");
        incident.resolve("FIXED", "done", "op-1");
        Instant muchLater = incident.getCreatedAt().plus(Duration.ofDays(30));

        IncidentResponse response = IncidentMapper.toResponse(incident, true, muchLater);

        assertEquals("MET", response.response().state());
        assertEquals("MET", response.resolution().state());
    }

    @Test
    @DisplayName("an SLA reached after its due date is breached even though it is finished")
    void slaFinishedLate() {
        Instant due = Instant.parse("2026-10-04T10:00:00Z");

        assertEquals("BREACHED", IncidentMapper.sla(due, due.plusSeconds(1), due.plusSeconds(2)).state());
        assertEquals("MET", IncidentMapper.sla(due, due, due.plusSeconds(2)).state());
        assertEquals("PENDING", IncidentMapper.sla(due, null, due).state());
        assertEquals("BREACHED", IncidentMapper.sla(due, null, due.plusSeconds(1)).state());
    }

    @Test
    @DisplayName("a cancelled incident has no SLA to meet")
    void cancelledHasNoSla() {
        Incident incident = incident();
        incident.cancel("op-1");

        IncidentResponse response = IncidentMapper.toResponse(incident, true, Instant.now());

        assertNull(response.response());
        assertNull(response.resolution());
        assertEquals("CANCELLED", response.status());
    }

    @Test
    @DisplayName("internal comments are left out unless asked for")
    void internalComments() {
        Incident incident = incident();
        incident.addComment(new Comment(UUID.randomUUID(), "op-1", "Public", false, null), "op-1");
        incident.addComment(new Comment(UUID.randomUUID(), "op-1", "Internal", true, null), "op-1");

        assertEquals(2, IncidentMapper.toResponse(incident, true, Instant.now()).comments().size());
        var requesterView = IncidentMapper.toResponse(incident, false, Instant.now()).comments();
        assertEquals(1, requesterView.size());
        assertEquals("Public", requesterView.get(0).text());
    }

    @Test
    @DisplayName("an audit entry maps with its statuses, the first one with no previous status")
    void auditEntry() {
        Incident incident = incident();
        incident.acknowledge("op-1");

        var first = IncidentMapper.toResponse(incident.getAuditTrail().get(0));
        var second = IncidentMapper.toResponse(incident.getAuditTrail().get(1));

        assertNull(first.fromStatus());
        assertEquals("NEW", first.toStatus());
        assertEquals("NEW", second.fromStatus());
        assertEquals("ACKNOWLEDGED", second.toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<IncidentMapper> constructor = IncidentMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
