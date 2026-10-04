package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/reopen}: why the resolution did not hold. */
@Serdeable
public record ReopenIncidentRequest(
        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {}
