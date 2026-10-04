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

/** DTO for Incident update (BIAN Behavior Qualifier: {@code update}). Replaces the details; a change of impact or urgency re-derives the priority. */
@Serdeable
public record UpdateIncidentRequest(
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
        Set<UUID> relatedChangeIds
) {}
