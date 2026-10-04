package com.thinklab.application.usecase;

import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.SlaTargets;
import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds {@code thinklab.incident.sla.*} (ADR-032): the response and resolution targets of each priority, keyed {@code P1..P4}. Any
 * priority not configured keeps the default, so a deployment only states what it changes.
 *
 * <pre>
 * thinklab.incident.sla.response.P1: PT10M
 * thinklab.incident.sla.resolution.P1: PT2H
 * </pre>
 */
@ConfigurationProperties("thinklab.incident.sla")
public class IncidentSlaProperties {

    private static final Map<Priority, Duration> DEFAULT_RESPONSE = Map.of(
            Priority.P1, Duration.ofMinutes(15), Priority.P2, Duration.ofMinutes(30), Priority.P3, Duration.ofHours(4), Priority.P4, Duration.ofHours(24));
    private static final Map<Priority, Duration> DEFAULT_RESOLUTION = Map.of(
            Priority.P1, Duration.ofHours(4), Priority.P2, Duration.ofHours(8), Priority.P3, Duration.ofDays(3), Priority.P4, Duration.ofDays(7));

    private Map<String, Duration> response = new LinkedHashMap<>();
    private Map<String, Duration> resolution = new LinkedHashMap<>();

    public Map<String, Duration> getResponse() {
        return response;
    }

    public void setResponse(Map<String, Duration> response) {
        this.response = response;
    }

    public Map<String, Duration> getResolution() {
        return resolution;
    }

    public void setResolution(Map<String, Duration> resolution) {
        this.resolution = resolution;
    }

    /** The targets of a priority: the configured ones, or the defaults. */
    public SlaTargets targetsFor(Priority priority) {
        return new SlaTargets(
                response.getOrDefault(priority.name(), DEFAULT_RESPONSE.get(priority)),
                resolution.getOrDefault(priority.name(), DEFAULT_RESOLUTION.get(priority)));
    }
}
