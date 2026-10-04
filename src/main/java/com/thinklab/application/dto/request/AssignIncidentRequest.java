package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTO for {@code assignment/update}: who works the incident. */
@Serdeable
public record AssignIncidentRequest(
        @NotNull(message = "Assignee is required")
        UUID assigneeId
) {}
