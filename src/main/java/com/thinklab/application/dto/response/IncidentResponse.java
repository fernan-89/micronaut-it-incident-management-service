package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for Incident output payload. {@code response} and {@code resolution} are the two SLA targets; they are {@code null} for a
 * cancelled incident, which has no SLA to meet. Internal comments are left out for a REQUESTER.
 */
@Serdeable
public record IncidentResponse(
        UUID id,
        UUID organisationId,
        UUID requesterId,
        String title,
        String description,
        String impact,
        String urgency,
        String priority,
        String status,
        UUID assigneeId,
        Set<UUID> affectedAssetIds,
        Set<UUID> relatedChangeIds,
        String holdReason,
        String resolutionCode,
        String resolutionNotes,
        int reopenCount,
        SlaResponse response,
        SlaResponse resolution,
        Instant acknowledgedAt,
        Instant resolvedAt,
        List<CommentResponse> comments,
        Instant createdAt,
        Instant updatedAt
) {}
