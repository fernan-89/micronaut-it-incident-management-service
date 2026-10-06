package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.Urgency;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the Incident Aggregate for MongoDB. */
@Introspected
public class IncidentDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID requesterId;
    private String title;
    private String description;
    private String impact;
    private String urgency;
    private String priority;
    private String status;
    private UUID assigneeId;
    private List<UUID> affectedAssetIds = new ArrayList<>();
    private List<UUID> relatedChangeIds = new ArrayList<>();
    private String holdReason;
    private String resolutionCode;
    private String resolutionNotes;
    private int reopenCount;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private List<CommentDocument> comments = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();
    private String idempotencyKey;

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getAssigneeId() { return assigneeId; }
    public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }
    public List<UUID> getAffectedAssetIds() { return affectedAssetIds; }
    public void setAffectedAssetIds(List<UUID> affectedAssetIds) { this.affectedAssetIds = affectedAssetIds; }
    public List<UUID> getRelatedChangeIds() { return relatedChangeIds; }
    public void setRelatedChangeIds(List<UUID> relatedChangeIds) { this.relatedChangeIds = relatedChangeIds; }
    public String getHoldReason() { return holdReason; }
    public void setHoldReason(String holdReason) { this.holdReason = holdReason; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public int getReopenCount() { return reopenCount; }
    public void setReopenCount(int reopenCount) { this.reopenCount = reopenCount; }
    public Instant getResponseDueAt() { return responseDueAt; }
    public void setResponseDueAt(Instant responseDueAt) { this.responseDueAt = responseDueAt; }
    public Instant getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(Instant resolutionDueAt) { this.resolutionDueAt = resolutionDueAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public List<CommentDocument> getComments() { return comments; }
    public void setComments(List<CommentDocument> comments) { this.comments = comments; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record CommentDocument(UUID commentId, String author, String text, boolean internal, Instant createdAt) {
        public static CommentDocument fromDomain(Comment comment) {
            return new CommentDocument(comment.commentId(), comment.author(), comment.text(), comment.internal(), comment.createdAt());
        }

        Comment toDomain() { return new Comment(commentId, author, text, internal, createdAt); }
    }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(IncidentAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name(), so a document this application wrote never has a null one.
        IncidentAuditEntry toDomain() {
            return new IncidentAuditEntry(occurredAt, action, executor, fromStatus != null ? IncidentStatus.valueOf(fromStatus) : null,
                    IncidentStatus.valueOf(toStatus), detail);
        }
    }

    public static final class IncidentPersistenceMapper {

        private IncidentPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static IncidentDocument toDocument(Incident incident) {
            IncidentDocument doc = new IncidentDocument();
            doc.setId(incident.getId());
            doc.setOrganisationId(incident.getOrganisationId());
            doc.setRequesterId(incident.getRequesterId());
            doc.setTitle(incident.getTitle());
            doc.setDescription(incident.getDescription());
            doc.setImpact(incident.getImpact().name());
            doc.setUrgency(incident.getUrgency().name());
            doc.setPriority(incident.getPriority().name());
            doc.setStatus(incident.getStatus().name());
            doc.setAssigneeId(incident.getAssigneeId());
            doc.setAffectedAssetIds(new ArrayList<>(incident.getAffectedAssetIds()));
            doc.setRelatedChangeIds(new ArrayList<>(incident.getRelatedChangeIds()));
            doc.setHoldReason(incident.getHoldReason());
            doc.setResolutionCode(incident.getResolutionCode());
            doc.setResolutionNotes(incident.getResolutionNotes());
            doc.setReopenCount(incident.getReopenCount());
            doc.setResponseDueAt(incident.getResponseDueAt());
            doc.setResolutionDueAt(incident.getResolutionDueAt());
            doc.setAcknowledgedAt(incident.getAcknowledgedAt());
            doc.setResolvedAt(incident.getResolvedAt());
            doc.setComments(incident.getComments().stream().map(CommentDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            doc.setCreatedAt(incident.getCreatedAt());
            doc.setUpdatedAt(incident.getUpdatedAt());
            doc.setAuditTrail(incident.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Incident toDomain(IncidentDocument doc) {
            return Incident.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getRequesterId(), doc.getTitle(), doc.getDescription(),
                    Impact.valueOf(doc.getImpact()), Urgency.valueOf(doc.getUrgency()), Priority.valueOf(doc.getPriority()),
                    IncidentStatus.valueOf(doc.getStatus()), doc.getAssigneeId(), new LinkedHashSet<>(doc.getAffectedAssetIds()),
                    new LinkedHashSet<>(doc.getRelatedChangeIds()), doc.getHoldReason(), doc.getResolutionCode(), doc.getResolutionNotes(),
                    doc.getReopenCount(), doc.getResponseDueAt(), doc.getResolutionDueAt(), doc.getAcknowledgedAt(), doc.getResolvedAt(),
                    doc.getComments().stream().map(CommentDocument::toDomain).collect(Collectors.toList()),
                    doc.getCreatedAt(), doc.getUpdatedAt(),
                    doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
