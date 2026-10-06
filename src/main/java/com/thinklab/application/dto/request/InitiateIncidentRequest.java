package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.Urgency;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * DTO for Incident creation (BIAN Behavior Qualifier: {@code initiate}). Priority is not part of it: it is derived from impact and
 * urgency (ADR-030). {@code requesterId} is required when staff file on a person's behalf and ignored for a REQUESTER (ADR-031).
 */
@Serdeable
public record InitiateIncidentRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Impact is required")
        Impact impact,

        @NotNull(message = "Urgency is required")
        Urgency urgency,

        @Nullable
        Set<UUID> affectedAssetIds,

        @Nullable
        Set<UUID> relatedChangeIds,

        @Nullable
        UUID requesterId,

        /** Staff only: a repeated key (per organisation) answers with the incident the first call opened instead of opening another. */
        @Nullable
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {}
