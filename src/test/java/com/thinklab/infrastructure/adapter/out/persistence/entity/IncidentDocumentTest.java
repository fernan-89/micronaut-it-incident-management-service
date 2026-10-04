package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.IncidentPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentDocumentTest {

    @Test
    @DisplayName("an incident that went through its whole life survives storage: state, links, SLA times, comments and the audit trail")
    void roundTrip() {
        UUID asset = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Incident incident = Incident.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Switch down", "No link", Impact.HIGH, Urgency.MEDIUM,
                Set.of(asset), Set.of(change), new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8)), "op-1");
        incident.assign(assignee, "op-1");
        incident.acknowledge("op-1");
        incident.start("op-1");
        incident.hold("vendor", "op-1");
        incident.resume("op-1");
        incident.addComment(new Comment(UUID.randomUUID(), "op-1", "Internal note", true, null), "op-1");
        incident.resolve("REPLACED", "Swapped", "op-1");
        incident.reopen("again", "op-1");
        incident.resolve("REPLACED", "Swapped again", "op-1");

        IncidentDocument document = IncidentPersistenceMapper.toDocument(incident);
        Incident restored = IncidentPersistenceMapper.toDomain(document);

        assertEquals(incident.getId(), restored.getId());
        assertEquals(IncidentStatus.RESOLVED, restored.getStatus());
        assertEquals("P2", restored.getPriority().name());
        assertEquals(assignee, restored.getAssigneeId());
        assertEquals(Set.of(asset), restored.getAffectedAssetIds());
        assertEquals(Set.of(change), restored.getRelatedChangeIds());
        assertEquals(1, restored.getReopenCount());
        assertEquals("REPLACED", restored.getResolutionCode());
        assertEquals(incident.getResponseDueAt(), restored.getResponseDueAt());
        assertEquals(incident.getResolutionDueAt(), restored.getResolutionDueAt());
        assertEquals(incident.getAcknowledgedAt(), restored.getAcknowledgedAt());
        assertEquals(incident.getResolvedAt(), restored.getResolvedAt());
        assertEquals(1, restored.getComments().size());
        assertEquals(true, restored.getComments().get(0).internal());
        assertEquals(incident.getAuditTrail().size(), restored.getAuditTrail().size());
        assertNull(restored.getAuditTrail().get(0).fromStatus());
        assertEquals(IncidentStatus.NEW, restored.getAuditTrail().get(1).fromStatus());
        assertEquals("op-1", document.getAuditTrail().get(0).executor());
        assertEquals(document.getId(), restored.getId());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<IncidentPersistenceMapper> constructor = IncidentPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
