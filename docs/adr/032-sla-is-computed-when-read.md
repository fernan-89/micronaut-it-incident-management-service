# ADR-032: SLA Targets Are Stored, Their State Is Computed When Read

## Status
Accepted

## Context
Each incident has a response target (until someone acknowledges it) and a resolution target (until it is resolved), both set by its priority. Knowing whether a target is met or breached could be done by a scheduler that flips a flag at the due time, or by comparing times when the incident is read.

## Decision
- The aggregate stores the two **due dates** (supplied by the application layer from `thinklab.incident.sla.response.P1..P4` / `resolution.P1..P4`, with ITIL-style defaults: P1 15 min / 4 h, P2 30 min / 8 h, P3 4 h / 3 d, P4 24 h / 7 d; a deployment overrides only what it states) and the **moments** the incident was acknowledged and resolved. The domain stays free of configuration and calendars.
- Whether a target is `PENDING`, `MET` or `BREACHED` is **computed when the incident is read**: once the target was reached (acknowledged / resolved) it is judged by that moment against the due date; while it is still running it is `BREACHED` as soon as now is past the due date. A cancelled incident has no SLA.
- **No scheduler, no background state:** nothing can be forgotten, drift, or be wrong after a restart, and a read is always consistent with the clock.
- The clock **does not pause while an incident is on hold**, and a reopened incident keeps its original resolution due date (the resolved time is cleared, so the target is running again).

## Consequences
- Positive: simple, stateless, testable with fixed times.
- Negative: no proactive breach alert (that belongs with monitoring and alerting, Journey 14), no business-hours calendar, no pause on hold; each can be added behind the same fields later (a calendar port as in the hardware-maintenance service).
