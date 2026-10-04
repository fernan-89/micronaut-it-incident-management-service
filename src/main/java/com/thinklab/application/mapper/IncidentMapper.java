package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.response.CommentResponse;
import com.thinklab.application.dto.response.IncidentAuditEntryResponse;
import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.dto.response.SlaResponse;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.SlaTargets;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Static factory mapper for Incident DTOs and the Domain aggregate. */
public final class IncidentMapper {

    private IncidentMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Incident toDomain(InitiateIncidentRequest request, UUID sovereignId, UUID organisationId, UUID requesterId,
                                    SlaTargets targets, String executor) {
        return Incident.createNew(sovereignId, organisationId, requesterId, request.title(), request.description(), request.impact(),
                request.urgency(), request.affectedAssetIds(), request.relatedChangeIds(), targets, executor);
    }

    /**
     * @param includeInternal false for a REQUESTER: internal comments are left out
     * @param now             the moment the SLA is judged at (a target still running is breached once it is past due)
     */
    public static IncidentResponse toResponse(Incident incident, boolean includeInternal, Instant now) {
        boolean cancelled = incident.getStatus() == IncidentStatus.CANCELLED;
        List<CommentResponse> comments = incident.getComments().stream()
                .filter(comment -> includeInternal || !comment.internal())
                .map(IncidentMapper::toResponse)
                .collect(Collectors.toList());
        return new IncidentResponse(incident.getId(), incident.getOrganisationId(), incident.getRequesterId(), incident.getTitle(),
                incident.getDescription(), incident.getImpact().name(), incident.getUrgency().name(), incident.getPriority().name(),
                incident.getStatus().name(), incident.getAssigneeId(), incident.getAffectedAssetIds(), incident.getRelatedChangeIds(),
                incident.getHoldReason(), incident.getResolutionCode(), incident.getResolutionNotes(), incident.getReopenCount(),
                cancelled ? null : sla(incident.getResponseDueAt(), incident.getAcknowledgedAt(), now),
                cancelled ? null : sla(incident.getResolutionDueAt(), incident.getResolvedAt(), now),
                incident.getAcknowledgedAt(), incident.getResolvedAt(), comments, incident.getCreatedAt(), incident.getUpdatedAt());
    }

    /** MET or BREACHED once the target has been reached (late or not); otherwise PENDING until the due date passes, then BREACHED. */
    static SlaResponse sla(Instant dueAt, Instant doneAt, Instant now) {
        String state;
        if (doneAt != null) {
            state = doneAt.isAfter(dueAt) ? "BREACHED" : "MET";
        } else {
            state = now.isAfter(dueAt) ? "BREACHED" : "PENDING";
        }
        return new SlaResponse(dueAt, state);
    }

    private static CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.commentId(), comment.author(), comment.text(), comment.internal(), comment.createdAt());
    }

    public static IncidentAuditEntryResponse toResponse(IncidentAuditEntry entry) {
        return new IncidentAuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
