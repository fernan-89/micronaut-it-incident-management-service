package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/hold}: putting an incident on hold needs a reason. */
@Serdeable
public record HoldIncidentRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {}
