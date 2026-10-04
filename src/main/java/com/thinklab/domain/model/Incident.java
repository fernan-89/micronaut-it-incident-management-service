package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidIncidentStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the Incident Aggregate Root (BIAN Service Domain: {@code it-incident-management}).
 *
 * <p><b>BIAN Alignment (ADR-013):</b> the Control Record of the incident-management Service Domain - an unplanned interruption of, or
 * degradation in, an IT service, scoped to an Organisation and filed by staff or, self-service, by the {@code REQUESTER} role
 * (ADR-031). Every route is a named Behavior Qualifier rather than a generic {@code control/{status}} endpoint, because several
 * transitions carry data of their own (hold needs a reason, resolve needs a resolution code and notes).
 *
 * <p><b>Priority is derived, never typed (ADR-030):</b> the caller states {@link Impact} and {@link Urgency}; {@link Priority#of} turns
 * them into P1..P4 with the ITIL matrix. Changing impact or urgency re-derives the priority and restarts the SLA targets from the
 * moment the incident was opened.
 *
 * <p><b>SLA is computed, not scheduled (ADR-032):</b> the aggregate stores the due dates (supplied by its caller from the configured
 * targets, so it stays calendar- and configuration-agnostic) and the moments the incident was acknowledged and resolved; whether a
 * target was met or breached is a question answered when it is read. The clock does not pause while an incident is on hold.
 *
 * <p><b>Links are references (ADR-033):</b> affected assets and related changes are stored as ids and are not validated against the
 * services that own them.
 *
 * <p><b>Forensic Audit Ledger:</b> every mutation appends an immutable {@link IncidentAuditEntry}.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class Incident {

    private final UUID id;
    private final UUID organisationId;
    private final UUID requesterId;
    private String title;
    private String description;
    private Impact impact;
    private Urgency urgency;
    private Priority priority;
    private IncidentStatus status;
    private UUID assigneeId;
    private final Set<UUID> affectedAssetIds;
    private final Set<UUID> relatedChangeIds;
    private String holdReason;
    private String resolutionCode;
    private String resolutionNotes;
    private int reopenCount;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private final List<Comment> comments;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<IncidentAuditEntry> auditTrail;

    private Incident(UUID id, UUID organisationId, UUID requesterId, String title, String description, Impact impact, Urgency urgency,
                     Set<UUID> affectedAssetIds, Set<UUID> relatedChangeIds, SlaTargets targets, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = Priority.of(impact, urgency);
        this.status = IncidentStatus.NEW;
        this.affectedAssetIds = copy(affectedAssetIds);
        this.relatedChangeIds = copy(relatedChangeIds);
        this.comments = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.responseDueAt = this.createdAt.plus(targets.response());
        this.resolutionDueAt = this.createdAt.plus(targets.resolution());
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new IncidentAuditEntry(this.createdAt, "INITIATED", executor, null, IncidentStatus.NEW,
                "Incident opened with priority " + priority + "."));
    }

    private Incident(UUID id, UUID organisationId, UUID requesterId, String title, String description, Impact impact, Urgency urgency,
                     Priority priority, IncidentStatus status, UUID assigneeId, Set<UUID> affectedAssetIds, Set<UUID> relatedChangeIds,
                     String holdReason, String resolutionCode, String resolutionNotes, int reopenCount, Instant responseDueAt,
                     Instant resolutionDueAt, Instant acknowledgedAt, Instant resolvedAt, List<Comment> comments, Instant createdAt,
                     Instant updatedAt, List<IncidentAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = priority != null ? priority : Priority.of(impact, urgency);
        this.status = status != null ? status : IncidentStatus.NEW;
        this.assigneeId = assigneeId;
        this.affectedAssetIds = copy(affectedAssetIds);
        this.relatedChangeIds = copy(relatedChangeIds);
        this.holdReason = holdReason;
        this.resolutionCode = resolutionCode;
        this.resolutionNotes = resolutionNotes;
        this.reopenCount = reopenCount;
        this.responseDueAt = responseDueAt;
        this.resolutionDueAt = resolutionDueAt;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Incident createNew(UUID id, UUID organisationId, UUID requesterId, String title, String description, Impact impact,
                                     Urgency urgency, Set<UUID> affectedAssetIds, Set<UUID> relatedChangeIds, SlaTargets targets, String executor) {
        if (id == null || organisationId == null || requesterId == null || impact == null || urgency == null || targets == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Requester ID, Impact, Urgency and SLA targets are mandatory for Incident creation.");
        }
        requireText(title, description);
        requireExecutor(executor);
        return new Incident(id, organisationId, requesterId, title, description, impact, urgency, affectedAssetIds, relatedChangeIds, targets, executor);
    }

    public static Incident reconstitute(UUID id, UUID organisationId, UUID requesterId, String title, String description, Impact impact,
                                        Urgency urgency, Priority priority, IncidentStatus status, UUID assigneeId, Set<UUID> affectedAssetIds,
                                        Set<UUID> relatedChangeIds, String holdReason, String resolutionCode, String resolutionNotes,
                                        int reopenCount, Instant responseDueAt, Instant resolutionDueAt, Instant acknowledgedAt,
                                        Instant resolvedAt, List<Comment> comments, Instant createdAt, Instant updatedAt,
                                        List<IncidentAuditEntry> auditTrail) {
        if (id == null || organisationId == null || requesterId == null || title == null || impact == null || urgency == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Requester ID, Title, Impact and Urgency are mandatory to reconstitute an Incident.");
        }
        return new Incident(id, organisationId, requesterId, title, description, impact, urgency, priority, status, assigneeId,
                affectedAssetIds, relatedChangeIds, holdReason, resolutionCode, resolutionNotes, reopenCount, responseDueAt,
                resolutionDueAt, acknowledgedAt, resolvedAt, comments, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /**
     * Behavior Qualifier: {@code update}. Only while the incident is still being worked. A change of impact or urgency re-derives the
     * priority and, when the priority changes, restarts the SLA targets from the moment the incident was opened.
     */
    public IncidentAuditEntry updateDetails(String newTitle, String newDescription, Impact newImpact, Urgency newUrgency,
                                            Set<UUID> newAffectedAssetIds, Set<UUID> newRelatedChangeIds, SlaTargets targetsForNewPriority,
                                            String executor) {
        requireStatus(IncidentStatus.NEW, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);
        requireText(newTitle, newDescription);
        Objects.requireNonNull(newImpact, "Impact is mandatory to update an Incident.");
        Objects.requireNonNull(newUrgency, "Urgency is mandatory to update an Incident.");
        Objects.requireNonNull(targetsForNewPriority, "SLA targets are mandatory to update an Incident.");
        requireExecutor(executor);
        Priority previous = this.priority;
        this.title = newTitle;
        this.description = newDescription;
        this.impact = newImpact;
        this.urgency = newUrgency;
        this.priority = Priority.of(newImpact, newUrgency);
        this.affectedAssetIds.clear();
        this.affectedAssetIds.addAll(copy(newAffectedAssetIds));
        this.relatedChangeIds.clear();
        this.relatedChangeIds.addAll(copy(newRelatedChangeIds));
        String detail = "Details updated.";
        if (this.priority != previous) {
            this.responseDueAt = createdAt.plus(targetsForNewPriority.response());
            this.resolutionDueAt = createdAt.plus(targetsForNewPriority.resolution());
            detail = "Details updated; priority " + previous + " -> " + this.priority + ", SLA targets restarted from the opening time.";
        }
        return record("UPDATED", executor, detail);
    }

    /** Behavior Qualifier: {@code assignment/update}. Names who works the incident; not a status change. */
    public IncidentAuditEntry assign(UUID newAssigneeId, String executor) {
        requireStatus(IncidentStatus.NEW, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);
        Objects.requireNonNull(newAssigneeId, "Assignee is mandatory to assign an Incident.");
        this.assigneeId = newAssigneeId;
        return record("ASSIGNED", executor, "Assigned to [" + newAssigneeId + "].");
    }

    /** Behavior Qualifier: {@code control/acknowledge}. NEW -&gt; ACKNOWLEDGED; stops the response SLA clock. */
    public IncidentAuditEntry acknowledge(String executor) {
        requireStatus(IncidentStatus.NEW);
        this.acknowledgedAt = Instant.now();
        return transition(IncidentStatus.ACKNOWLEDGED, "ACKNOWLEDGED", executor, "Incident acknowledged.");
    }

    /** Behavior Qualifier: {@code control/start}. ACKNOWLEDGED -&gt; IN_PROGRESS. */
    public IncidentAuditEntry start(String executor) {
        requireStatus(IncidentStatus.ACKNOWLEDGED);
        return transition(IncidentStatus.IN_PROGRESS, "WORK_STARTED", executor, "Work started.");
    }

    /** Behavior Qualifier: {@code control/hold}. IN_PROGRESS -&gt; ON_HOLD; the reason is mandatory. */
    public IncidentAuditEntry hold(String reason, String executor) {
        requireStatus(IncidentStatus.IN_PROGRESS);
        this.holdReason = requireNonBlank(reason, "A reason is mandatory to put an Incident on hold.");
        return transition(IncidentStatus.ON_HOLD, "PUT_ON_HOLD", executor, "Put on hold: " + reason + ".");
    }

    /** Behavior Qualifier: {@code control/resume}. ON_HOLD -&gt; IN_PROGRESS. */
    public IncidentAuditEntry resume(String executor) {
        requireStatus(IncidentStatus.ON_HOLD);
        this.holdReason = null;
        return transition(IncidentStatus.IN_PROGRESS, "WORK_RESUMED", executor, "Work resumed.");
    }

    /** Behavior Qualifier: {@code control/resolve}. ACKNOWLEDGED or IN_PROGRESS -&gt; RESOLVED; code and notes are mandatory. */
    public IncidentAuditEntry resolve(String newResolutionCode, String newResolutionNotes, String executor) {
        requireStatus(IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
        this.resolutionCode = requireNonBlank(newResolutionCode, "A resolution code is mandatory to resolve an Incident.");
        this.resolutionNotes = requireNonBlank(newResolutionNotes, "Resolution notes are mandatory to resolve an Incident.");
        this.resolvedAt = Instant.now();
        return transition(IncidentStatus.RESOLVED, "RESOLVED", executor, "Resolved with code " + newResolutionCode + ".");
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    public IncidentAuditEntry close(String executor) {
        requireStatus(IncidentStatus.RESOLVED);
        return transition(IncidentStatus.CLOSED, "CLOSED", executor, "Closed.");
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; IN_PROGRESS; the resolution clock runs again. */
    public IncidentAuditEntry reopen(String reason, String executor) {
        requireStatus(IncidentStatus.RESOLVED);
        requireNonBlank(reason, "A reason is mandatory to reopen an Incident.");
        this.resolvedAt = null;
        this.reopenCount++;
        return transition(IncidentStatus.IN_PROGRESS, "REOPENED", executor, "Reopened: " + reason + ".");
    }

    /** Behavior Qualifier: {@code control/cancel} (terminal, replaces DELETE). Only before it is resolved. */
    public IncidentAuditEntry cancel(String executor) {
        requireStatus(IncidentStatus.NEW, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);
        return transition(IncidentStatus.CANCELLED, "CANCELLED", executor, "Cancelled.");
    }

    /** Behavior Qualifier: {@code comment/initiate}. Not available once the incident is terminal. */
    public IncidentAuditEntry addComment(Comment comment, String executor) {
        requireNotTerminal("comment on");
        Objects.requireNonNull(comment, "comment is mandatory.");
        this.comments.add(comment);
        return record("COMMENT_ADDED", executor, comment.internal() ? "Internal comment added." : "Comment added.");
    }

    // --- Internal helpers ---

    private IncidentAuditEntry transition(IncidentStatus newStatus, String action, String executor, String detail) {
        requireExecutor(executor);
        IncidentStatus previous = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        IncidentAuditEntry entry = new IncidentAuditEntry(this.updatedAt, action, executor, previous, newStatus, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private IncidentAuditEntry record(String action, String executor, String detail) {
        requireExecutor(executor);
        this.updatedAt = Instant.now();
        IncidentAuditEntry entry = new IncidentAuditEntry(this.updatedAt, action, executor, this.status, this.status, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(IncidentStatus... allowed) {
        if (Arrays.asList(allowed).contains(this.status)) {
            return;
        }
        throw new InvalidIncidentStatusException(String.format(
                "Illegal transition: Incident is [%s], expected one of %s.", this.status, Arrays.toString(allowed)));
    }

    private void requireNotTerminal(String operation) {
        if (this.status == IncidentStatus.CLOSED || this.status == IncidentStatus.CANCELLED) {
            throw new InvalidIncidentStatusException(String.format(
                    "Compliance Violation: cannot %s a %s Incident; the lifecycle is terminal.", operation, this.status));
        }
    }

    private static void requireText(String title, String description) {
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Title and Description are mandatory for an Incident.");
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Incident mutations.");
        }
    }

    private static Set<UUID> copy(Set<UUID> ids) {
        return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getRequesterId() { return requesterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Impact getImpact() { return impact; }
    public Urgency getUrgency() { return urgency; }
    public Priority getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public UUID getAssigneeId() { return assigneeId; }
    public Set<UUID> getAffectedAssetIds() { return Collections.unmodifiableSet(affectedAssetIds); }
    public Set<UUID> getRelatedChangeIds() { return Collections.unmodifiableSet(relatedChangeIds); }
    public String getHoldReason() { return holdReason; }
    public String getResolutionCode() { return resolutionCode; }
    public String getResolutionNotes() { return resolutionNotes; }
    public int getReopenCount() { return reopenCount; }
    public Instant getResponseDueAt() { return responseDueAt; }
    public Instant getResolutionDueAt() { return resolutionDueAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public List<Comment> getComments() { return Collections.unmodifiableList(comments); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<IncidentAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    /** How far the incident reaches (users, services). */
    public enum Impact { LOW, MEDIUM, HIGH }

    /** How soon it must be dealt with. */
    public enum Urgency { LOW, MEDIUM, HIGH }

    /** ITIL priority, derived from impact and urgency. */
    public enum Priority {
        P1, P2, P3, P4;

        /** HIGH/HIGH is P1; one HIGH with a MEDIUM is P2; HIGH with LOW, or MEDIUM/MEDIUM, is P3; everything else is P4. */
        public static Priority of(Impact impact, Urgency urgency) {
            int score = impact.ordinal() + urgency.ordinal();
            return switch (score) {
                case 4 -> P1;
                case 3 -> P2;
                case 2 -> P3;
                default -> P4;
            };
        }
    }

    /** The response and resolution targets of one priority; the application layer supplies them from configuration. */
    public record SlaTargets(Duration response, Duration resolution) {
        public SlaTargets {
            Objects.requireNonNull(response, "response cannot be null.");
            Objects.requireNonNull(resolution, "resolution cannot be null.");
            if (response.isNegative() || response.isZero() || resolution.isNegative() || resolution.isZero()) {
                throw new IllegalArgumentException("SLA targets must be positive durations.");
            }
        }
    }

    /**
     * <pre>
     * NEW -&gt; ACKNOWLEDGED -&gt; IN_PROGRESS &lt;-&gt; ON_HOLD
     *              |             |
     *              +------+------+
     *                     v
     *                 RESOLVED -&gt; CLOSED (terminal)
     *                     |
     *                     v (reopen)
     *                 IN_PROGRESS
     * NEW, ACKNOWLEDGED, IN_PROGRESS, ON_HOLD -&gt; CANCELLED (terminal)
     * </pre>
     */
    public enum IncidentStatus { NEW, ACKNOWLEDGED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED }

    /**
     * Immutable forensic ledger entry, mirroring the platform's established audit-trail pattern.
     *
     * @param fromStatus status before the action ({@code null} for the initiating entry)
     * @param toStatus   status after the action (equal to {@code fromStatus} for non-transition actions)
     */
    public record IncidentAuditEntry(Instant occurredAt, String action, String executor, IncidentStatus fromStatus, IncidentStatus toStatus, String detail) {}

    /** @param internal invisible to a REQUESTER-scoped read (application-layer filtering). */
    public record Comment(UUID commentId, String author, String text, boolean internal, Instant createdAt) {
        public Comment {
            Objects.requireNonNull(commentId, "commentId cannot be null.");
            if (author == null || author.isBlank()) {
                throw new IllegalArgumentException("Comment author cannot be blank.");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Comment text cannot be blank.");
            }
            createdAt = createdAt != null ? createdAt : Instant.now();
        }
    }
}
