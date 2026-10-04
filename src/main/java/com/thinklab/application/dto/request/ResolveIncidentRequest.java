package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/resolve}: how the incident was resolved, as a short code and notes. */
@Serdeable
public record ResolveIncidentRequest(
        @NotBlank(message = "A resolution code is required")
        @Size(max = 100, message = "Resolution code must not exceed 100 characters")
        String resolutionCode,

        @NotBlank(message = "Resolution notes are required")
        @Size(max = 4000, message = "Resolution notes must not exceed 4000 characters")
        String notes
) {}
