package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidIncidentStatusException;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentTest {

    private static final String EXECUTOR = "op-1";
    private static final SlaTargets TARGETS = new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8));
    private static final SlaTargets OTHER_TARGETS = new SlaTargets(Duration.ofMinutes(15), Duration.ofHours(4));

    private final UUID id = UUID.randomUUID();
    private final UUID organisationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID changeId = UUID.randomUUID();

    private Incident incident() {
        return Incident.createNew(id, organisationId, requesterId, "Switch down", "No link on floor 3", Impact.MEDIUM, Urgency.MEDIUM,
                Set.of(assetId), Set.of(changeId), TARGETS, EXECUTOR);
    }

    private Incident inProgress() {
        Incident incident = incident();
        incident.acknowledge(EXECUTOR);
        incident.start(EXECUTOR);
        return incident;
    }

    private Incident resolved() {
        Incident incident = inProgress();
        incident.resolve("REPLACED", "Swapped the switch", EXECUTOR);
        return incident;
    }

    // ------------------------------------------------------------ creation

    @Test
    @DisplayName("createNew opens a NEW incident whose priority comes from impact and urgency and whose due dates come from the targets")
    void createNew() {
        Incident incident = incident();

        assertEquals(IncidentStatus.NEW, incident.getStatus());
        assertEquals(Priority.P3, incident.getPriority());
        assertEquals(incident.getCreatedAt().plus(Duration.ofMinutes(30)), incident.getResponseDueAt());
        assertEquals(incident.getCreatedAt().plus(Duration.ofHours(8)), incident.getResolutionDueAt());
        assertEquals(Set.of(assetId), incident.getAffectedAssetIds());
        assertEquals(Set.of(changeId), incident.getRelatedChangeIds());
        assertEquals(1, incident.getAuditTrail().size());
        assertNull(incident.getAuditTrail().get(0).fromStatus());
        assertEquals(0, incident.getReopenCount());
        assertNull(incident.getAssigneeId());
        assertTrue(incident.getComments().isEmpty());
    }

    @Test
    @DisplayName("null link sets are treated as empty")
    void nullLinks() {
        Incident incident = Incident.createNew(id, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR);

        assertTrue(incident.getAffectedAssetIds().isEmpty());
        assertTrue(incident.getRelatedChangeIds().isEmpty());
    }

    @Test
    @DisplayName("createNew rejects missing identity, impact, urgency, targets, text and executor")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(null, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, null, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, null, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", "d", null, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", "d", Impact.LOW, null, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, null, "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, " ", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", null, Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", " ", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, null));
        assertThrows(IllegalArgumentException.class, () -> Incident.createNew(id, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, " "));
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory identity and defaults missing status, priority, collections and timestamps")
    void reconstitute() {
        assertThrows(IllegalArgumentException.class, () -> reconstitute(null, requesterId, "t", Impact.LOW, Urgency.LOW));
        assertThrows(IllegalArgumentException.class, () -> Incident.reconstitute(null, organisationId, requesterId, "t", "d", Impact.LOW, Urgency.LOW, null, null, null, null, null, null, null, null, 0,
                null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> reconstitute(organisationId, null, "t", Impact.LOW, Urgency.LOW));
        assertThrows(IllegalArgumentException.class, () -> reconstitute(organisationId, requesterId, null, Impact.LOW, Urgency.LOW));
        assertThrows(IllegalArgumentException.class, () -> reconstitute(organisationId, requesterId, "t", null, Urgency.LOW));
        assertThrows(IllegalArgumentException.class, () -> reconstitute(organisationId, requesterId, "t", Impact.LOW, null));

        Incident restored = reconstitute(organisationId, requesterId, "t", Impact.HIGH, Urgency.HIGH);

        assertEquals(IncidentStatus.NEW, restored.getStatus());
        assertEquals(Priority.P1, restored.getPriority());
        assertNotNull(restored.getCreatedAt());
        assertEquals(restored.getCreatedAt(), restored.getUpdatedAt());
        assertTrue(restored.getComments().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
        assertTrue(restored.getAffectedAssetIds().isEmpty());
    }

    private Incident reconstitute(UUID org, UUID requester, String title, Impact impact, Urgency urgency) {
        return Incident.reconstitute(id, org, requester, title, "d", impact, urgency, null, null, null, null, null, null, null, null, 0,
                null, null, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------ priority and SLA values

    @Test
    @DisplayName("the priority matrix: HIGH/HIGH is P1, one HIGH with a MEDIUM is P2, HIGH with LOW or MEDIUM/MEDIUM is P3, the rest P4")
    void priorityMatrix() {
        assertEquals(Priority.P1, Priority.of(Impact.HIGH, Urgency.HIGH));
        assertEquals(Priority.P2, Priority.of(Impact.HIGH, Urgency.MEDIUM));
        assertEquals(Priority.P2, Priority.of(Impact.MEDIUM, Urgency.HIGH));
        assertEquals(Priority.P3, Priority.of(Impact.HIGH, Urgency.LOW));
        assertEquals(Priority.P3, Priority.of(Impact.MEDIUM, Urgency.MEDIUM));
        assertEquals(Priority.P3, Priority.of(Impact.LOW, Urgency.HIGH));
        assertEquals(Priority.P4, Priority.of(Impact.MEDIUM, Urgency.LOW));
        assertEquals(Priority.P4, Priority.of(Impact.LOW, Urgency.MEDIUM));
        assertEquals(Priority.P4, Priority.of(Impact.LOW, Urgency.LOW));
    }

    @Test
    @DisplayName("SLA targets must be present and positive")
    void slaTargets() {
        assertThrows(NullPointerException.class, () -> new SlaTargets(null, Duration.ofHours(1)));
        assertThrows(NullPointerException.class, () -> new SlaTargets(Duration.ofHours(1), null));
        assertThrows(IllegalArgumentException.class, () -> new SlaTargets(Duration.ZERO, Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new SlaTargets(Duration.ofHours(-1), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new SlaTargets(Duration.ofHours(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SlaTargets(Duration.ofHours(1), Duration.ofHours(-1)));
    }

    // ------------------------------------------------------------ update

    @Test
    @DisplayName("update replaces the details; a change that leads to another priority restarts the SLA targets from the opening time")
    void updateRestartsTheSlaWhenThePriorityChanges() {
        Incident incident = incident();
        UUID otherAsset = UUID.randomUUID();

        IncidentAuditEntry entry = incident.updateDetails("Core switch down", "Whole site", Impact.HIGH, Urgency.HIGH, Set.of(otherAsset), null, OTHER_TARGETS, EXECUTOR);

        assertEquals("Core switch down", incident.getTitle());
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(Set.of(otherAsset), incident.getAffectedAssetIds());
        assertTrue(incident.getRelatedChangeIds().isEmpty());
        assertEquals(incident.getCreatedAt().plus(Duration.ofMinutes(15)), incident.getResponseDueAt());
        assertEquals(incident.getCreatedAt().plus(Duration.ofHours(4)), incident.getResolutionDueAt());
        assertTrue(entry.detail().contains("P3 -> P1"));
        assertEquals("UPDATED", entry.action());
    }

    @Test
    @DisplayName("update that keeps the priority keeps the due dates")
    void updateKeepsTheSlaWhenThePriorityIsTheSame() {
        Incident incident = incident();
        Instant response = incident.getResponseDueAt();

        IncidentAuditEntry entry = incident.updateDetails("New title", "New description", Impact.LOW, Urgency.HIGH, null, null, OTHER_TARGETS, EXECUTOR);

        assertEquals(Priority.P3, incident.getPriority());
        assertEquals(response, incident.getResponseDueAt());
        assertEquals("Details updated.", entry.detail());
    }

    @Test
    @DisplayName("update is refused once the incident is resolved or finished, and needs its inputs")
    void updateGuards() {
        assertThrows(InvalidIncidentStatusException.class, () -> resolved().updateDetails("t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        Incident incident = incident();
        assertThrows(IllegalArgumentException.class, () -> incident.updateDetails(" ", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.updateDetails("t", null, Impact.LOW, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(NullPointerException.class, () -> incident.updateDetails("t", "d", null, Urgency.LOW, null, null, TARGETS, EXECUTOR));
        assertThrows(NullPointerException.class, () -> incident.updateDetails("t", "d", Impact.LOW, null, null, null, TARGETS, EXECUTOR));
        assertThrows(NullPointerException.class, () -> incident.updateDetails("t", "d", Impact.LOW, Urgency.LOW, null, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.updateDetails("t", "d", Impact.LOW, Urgency.LOW, null, null, TARGETS, " "));
    }

    // ------------------------------------------------------------ assignment

    @Test
    @DisplayName("assign names the assignee without changing the status, while the incident is open")
    void assign() {
        Incident incident = incident();
        UUID assignee = UUID.randomUUID();

        IncidentAuditEntry entry = incident.assign(assignee, EXECUTOR);

        assertEquals(assignee, incident.getAssigneeId());
        assertEquals(IncidentStatus.NEW, incident.getStatus());
        assertEquals("ASSIGNED", entry.action());
        assertEquals(IncidentStatus.NEW, entry.fromStatus());
        assertEquals(IncidentStatus.NEW, entry.toStatus());
        assertThrows(NullPointerException.class, () -> incident.assign(null, EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> resolved().assign(assignee, EXECUTOR));
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    @DisplayName("the happy path: acknowledge, start, resolve, close; the audit trail records each step")
    void happyPath() {
        Incident incident = incident();

        var acknowledged = incident.acknowledge(EXECUTOR);
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertNotNull(incident.getAcknowledgedAt());
        assertEquals(IncidentStatus.NEW, acknowledged.fromStatus());

        incident.start(EXECUTOR);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        var resolution = incident.resolve("REPLACED", "Swapped the switch", EXECUTOR);
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals("REPLACED", incident.getResolutionCode());
        assertEquals("Swapped the switch", incident.getResolutionNotes());
        assertNotNull(incident.getResolvedAt());
        assertTrue(resolution.detail().contains("REPLACED"));

        incident.close(EXECUTOR);
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertEquals(List.of("INITIATED", "ACKNOWLEDGED", "WORK_STARTED", "RESOLVED", "CLOSED"),
                incident.getAuditTrail().stream().map(IncidentAuditEntry::action).toList());
    }

    @Test
    @DisplayName("an acknowledged incident can be resolved without being started")
    void resolveFromAcknowledged() {
        Incident incident = incident();
        incident.acknowledge(EXECUTOR);

        incident.resolve("FIXED", "Fixed on the phone", EXECUTOR);

        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
    }

    @Test
    @DisplayName("hold needs a reason and resume clears it")
    void holdAndResume() {
        Incident incident = inProgress();

        incident.hold("Waiting for the vendor", EXECUTOR);
        assertEquals(IncidentStatus.ON_HOLD, incident.getStatus());
        assertEquals("Waiting for the vendor", incident.getHoldReason());

        incident.resume(EXECUTOR);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getHoldReason());

        assertThrows(IllegalArgumentException.class, () -> incident.hold(null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.hold(" ", EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> incident.resume(EXECUTOR));
    }

    @Test
    @DisplayName("resolve needs a code and notes")
    void resolveGuards() {
        Incident incident = inProgress();

        assertThrows(IllegalArgumentException.class, () -> incident.resolve(null, "n", EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.resolve(" ", "n", EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.resolve("C", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> incident.resolve("C", " ", EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> incident().resolve("C", "n", EXECUTOR));
    }

    @Test
    @DisplayName("reopen puts a resolved incident back in progress, clears the resolution time and counts it; it needs a reason")
    void reopen() {
        Incident incident = resolved();

        incident.reopen("Still failing", EXECUTOR);

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertEquals(1, incident.getReopenCount());
        assertThrows(IllegalArgumentException.class, () -> resolved().reopen(null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> resolved().reopen(" ", EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> inProgress().reopen("why", EXECUTOR));
    }

    @Test
    @DisplayName("cancel is legal before the incident is resolved and not after")
    void cancel() {
        for (Incident open : List.of(incident(), acknowledged(), inProgress(), onHold())) {
            open.cancel(EXECUTOR);
            assertEquals(IncidentStatus.CANCELLED, open.getStatus());
        }
        assertThrows(InvalidIncidentStatusException.class, () -> resolved().cancel(EXECUTOR));
    }

    private Incident acknowledged() {
        Incident incident = incident();
        incident.acknowledge(EXECUTOR);
        return incident;
    }

    private Incident onHold() {
        Incident incident = inProgress();
        incident.hold("waiting", EXECUTOR);
        return incident;
    }

    @Test
    @DisplayName("illegal transitions are refused with the status they were attempted from")
    void illegalTransitions() {
        assertThrows(InvalidIncidentStatusException.class, () -> incident().start(EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> incident().hold("r", EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> incident().close(EXECUTOR));
        assertThrows(InvalidIncidentStatusException.class, () -> acknowledged().acknowledge(EXECUTOR));
        var failure = assertThrows(InvalidIncidentStatusException.class, () -> resolved().start(EXECUTOR));
        assertTrue(failure.getMessage().contains("RESOLVED"));
    }

    @Test
    @DisplayName("every mutation needs an executor")
    void executorIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> incident().acknowledge(null));
        assertThrows(IllegalArgumentException.class, () -> incident().assign(UUID.randomUUID(), " "));
        assertThrows(IllegalArgumentException.class, () -> incident().addComment(new Comment(UUID.randomUUID(), "a", "t", false, null), null));
    }

    // ------------------------------------------------------------ comments

    @Test
    @DisplayName("comments are added while the incident is not terminal; internal ones are marked in the trail")
    void comments() {
        Incident incident = resolved();

        var publicEntry = incident.addComment(new Comment(UUID.randomUUID(), "op-1", "Checked the port", false, null), EXECUTOR);
        var internalEntry = incident.addComment(new Comment(UUID.randomUUID(), "op-1", "Do not tell them", true, null), EXECUTOR);

        assertEquals(2, incident.getComments().size());
        assertNotNull(incident.getComments().get(0).createdAt());
        assertEquals("Comment added.", publicEntry.detail());
        assertEquals("Internal comment added.", internalEntry.detail());
        assertEquals(IncidentStatus.RESOLVED, publicEntry.toStatus());

        incident.close(EXECUTOR);
        assertThrows(InvalidIncidentStatusException.class, () -> incident.addComment(new Comment(UUID.randomUUID(), "a", "t", false, null), EXECUTOR));
        assertThrows(NullPointerException.class, () -> incident().addComment(null, EXECUTOR));

        Incident cancelled = incident();
        cancelled.cancel(EXECUTOR);
        assertThrows(InvalidIncidentStatusException.class, () -> cancelled.addComment(new Comment(UUID.randomUUID(), "a", "t", false, null), EXECUTOR));
    }

    @Test
    @DisplayName("a comment needs an id, an author and a text")
    void commentGuards() {
        assertThrows(NullPointerException.class, () -> new Comment(null, "a", "t", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), null, "t", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), " ", "t", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "a", null, false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "a", " ", false, null));
        Instant at = Instant.parse("2026-10-04T10:00:00Z");
        assertEquals(at, new Comment(UUID.randomUUID(), "a", "t", false, at).createdAt());
    }

    @Test
    @DisplayName("getters expose what the aggregate holds")
    void getters() {
        Incident incident = incident();

        assertEquals(id, incident.getId());
        assertEquals(organisationId, incident.getOrganisationId());
        assertEquals(requesterId, incident.getRequesterId());
        assertEquals("No link on floor 3", incident.getDescription());
        assertEquals(Impact.MEDIUM, incident.getImpact());
        assertEquals(Urgency.MEDIUM, incident.getUrgency());
        assertNull(incident.getResolutionCode());
        assertNull(incident.getResolutionNotes());
        assertNull(incident.getAcknowledgedAt());
        assertNull(incident.getResolvedAt());
        assertNotNull(incident.getUpdatedAt());
    }
}
