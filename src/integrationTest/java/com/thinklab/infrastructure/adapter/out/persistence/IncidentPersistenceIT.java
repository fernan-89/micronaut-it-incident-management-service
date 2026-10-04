package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.exception.InvalidIncidentStatusException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.domain.repository.IncidentRepository;
import com.thinklab.domain.repository.IncidentRepository.Filter;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Incident aggregate through its repository against a real MongoDB: the guarded save (a second writer from the same state loses),
 * tenant scoping, every collection filter, comments, and the four indexes
 * {@link com.thinklab.infrastructure.adapter.out.persistence.repository.IncidentIndexInitializer} creates at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "incident_it";
    private static final String EXECUTOR = "op-1";
    private static final SlaTargets TARGETS = new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8));

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    IncidentRepository incidents;

    @Inject
    MongoClient mongoClient;

    private Incident newIncident(UUID organisation, UUID requester, Set<UUID> assets) {
        return Incident.createNew(UUID.randomUUID(), organisation, requester, "Switch down", "No link", Impact.MEDIUM, Urgency.MEDIUM, assets, null, TARGETS, EXECUTOR);
    }

    private static Set<UUID> ids(List<Incident> found) {
        return found.stream().map(Incident::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a created incident is read back whole: links, SLA due dates and its INITIATED audit entry")
    void createAndFind() {
        UUID organisation = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        Incident created = incidents.create(newIncident(organisation, UUID.randomUUID(), Set.of(asset))).block();

        Incident found = incidents.findById(created.getId(), organisation).block();

        assertEquals(IncidentStatus.NEW, found.getStatus());
        assertEquals(Priority.P3, found.getPriority());
        assertEquals(Set.of(asset), found.getAffectedAssetIds());
        assertEquals(created.getResponseDueAt().toEpochMilli(), found.getResponseDueAt().toEpochMilli());
        assertEquals(1, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("another organisation's incident is simply not found")
    void tenantIsolation() {
        UUID organisation = UUID.randomUUID();
        Incident created = incidents.create(newIncident(organisation, UUID.randomUUID(), null)).block();

        assertNull(incidents.findById(created.getId(), UUID.randomUUID()).block());
        assertTrue(incidents.findAll(UUID.randomUUID(), new Filter(null, null, null, null, null, false)).collectList().block().isEmpty());
    }

    @Test
    @DisplayName("save persists the whole state the aggregate reached together with its audit entry")
    void saveAppliesTheTransition() {
        UUID organisation = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Incident created = incidents.create(newIncident(organisation, UUID.randomUUID(), null)).block();
        Incident loaded = incidents.findById(created.getId(), organisation).block();
        loaded.assign(assignee, EXECUTOR);
        var entry = loaded.acknowledge(EXECUTOR);

        incidents.save(loaded, IncidentStatus.NEW, entry).block();

        Incident found = incidents.findById(created.getId(), organisation).block();
        assertEquals(IncidentStatus.ACKNOWLEDGED, found.getStatus());
        assertEquals(assignee, found.getAssigneeId());
        assertEquals(loaded.getAcknowledgedAt().toEpochMilli(), found.getAcknowledgedAt().toEpochMilli());
        assertEquals(2, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("two people working the same incident from the same state cannot both win: the second save is refused, not merged")
    void concurrentWritersAreGuarded() {
        UUID organisation = UUID.randomUUID();
        Incident created = incidents.create(newIncident(organisation, UUID.randomUUID(), null)).block();
        Incident seenByA = incidents.findById(created.getId(), organisation).block();
        Incident seenByB = incidents.findById(created.getId(), organisation).block();
        var entryA = seenByA.acknowledge("op-a");
        var entryB = seenByB.cancel("op-b");

        incidents.save(seenByA, IncidentStatus.NEW, entryA).block();

        assertThrows(InvalidIncidentStatusException.class, () -> incidents.save(seenByB, IncidentStatus.NEW, entryB).block());
        Incident found = incidents.findById(created.getId(), organisation).block();
        assertEquals(IncidentStatus.ACKNOWLEDGED, found.getStatus());
        assertEquals(2, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("addComment pushes the comment and its audit entry; an unknown incident is IncidentNotFoundException")
    void comments() {
        UUID organisation = UUID.randomUUID();
        Incident created = incidents.create(newIncident(organisation, UUID.randomUUID(), null)).block();
        var comment = new Comment(UUID.randomUUID(), EXECUTOR, "Checked the port", true, null);
        var entry = created.addComment(comment, EXECUTOR);

        incidents.addComment(created.getId(), organisation, comment, entry).block();

        Incident found = incidents.findById(created.getId(), organisation).block();
        assertEquals(1, found.getComments().size());
        assertTrue(found.getComments().get(0).internal());
        assertEquals(2, found.getAuditTrail().size());
        assertThrows(IncidentNotFoundException.class, () -> incidents.addComment(UUID.randomUUID(), organisation, comment, entry).block());
        assertThrows(IncidentNotFoundException.class, () -> incidents.addComment(created.getId(), UUID.randomUUID(), comment, entry).block());
    }

    @Test
    @DisplayName("the collection honours status, priority, assignee, affected asset, requester and open-only, always inside the tenant")
    void filters() {
        UUID organisation = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        Incident open = incidents.create(newIncident(organisation, requester, Set.of(asset))).block();
        Incident critical = incidents.create(Incident.createNew(UUID.randomUUID(), organisation, UUID.randomUUID(), "Core down", "All", Impact.HIGH, Urgency.HIGH,
                null, null, TARGETS, EXECUTOR)).block();
        Incident cancelled = incidents.create(newIncident(organisation, UUID.randomUUID(), null)).block();
        incidents.create(newIncident(UUID.randomUUID(), requester, Set.of(asset))).block();
        var assignment = open.assign(assignee, EXECUTOR);
        incidents.save(open, IncidentStatus.NEW, assignment).block();
        var cancellation = cancelled.cancel(EXECUTOR);
        incidents.save(cancelled, IncidentStatus.NEW, cancellation).block();

        assertEquals(Set.of(open.getId(), critical.getId(), cancelled.getId()), ids(incidents.findAll(organisation, new Filter(null, null, null, null, null, false)).collectList().block()));
        assertEquals(Set.of(open.getId(), critical.getId()), ids(incidents.findAll(organisation, new Filter(null, null, null, null, null, true)).collectList().block()));
        assertEquals(Set.of(cancelled.getId()), ids(incidents.findAll(organisation, new Filter(IncidentStatus.CANCELLED, null, null, null, null, false)).collectList().block()));
        assertEquals(Set.of(critical.getId()), ids(incidents.findAll(organisation, new Filter(null, Priority.P1, null, null, null, false)).collectList().block()));
        assertEquals(Set.of(open.getId()), ids(incidents.findAll(organisation, new Filter(null, null, assignee, null, null, false)).collectList().block()));
        assertEquals(Set.of(open.getId()), ids(incidents.findAll(organisation, new Filter(null, null, null, asset, null, false)).collectList().block()));
        assertEquals(Set.of(open.getId()), ids(incidents.findAll(organisation, new Filter(null, null, null, null, requester, false)).collectList().block()));
    }

    @Test
    @DisplayName("the four compound indexes exist on incidents")
    void indexesExist() {
        incidents.create(newIncident(UUID.randomUUID(), UUID.randomUUID(), null)).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("incidents").listIndexes()).collectList().block();
        Set<Document> keys = indexes.stream().map(index -> index.get("key", Document.class)).collect(Collectors.toSet());

        assertTrue(keys.contains(new Document("organisationId", 1).append("status", 1).append("priority", 1)), () -> "incidents: " + indexes);
        assertTrue(keys.contains(new Document("organisationId", 1).append("assigneeId", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("requesterId", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("affectedAssetIds", 1)));
    }
}
