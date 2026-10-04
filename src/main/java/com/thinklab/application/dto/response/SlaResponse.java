package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * One SLA target of an incident. {@code state} is {@code PENDING} (still running, not yet late), {@code MET} or {@code BREACHED} (late,
 * whether it is still running or it finished late) - worked out when the incident is read, not by a background job (ADR-032).
 */
@Serdeable
public record SlaResponse(Instant dueAt, String state) {}
