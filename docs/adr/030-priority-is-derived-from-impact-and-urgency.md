# ADR-030: Priority Is Derived From Impact and Urgency, Never Typed

## Status
Accepted

## Context
Incidents arrive from people who judge their own problem the most urgent one there is. If the caller types the priority, "P1" becomes the default answer and the queue loses its meaning; if staff must triage before anything has a priority, every incident starts without a clock.

## Decision
- The caller states two facts: **impact** (LOW, MEDIUM, HIGH: how far it reaches) and **urgency** (LOW, MEDIUM, HIGH: how soon it must be dealt with). The priority is **derived** with the ITIL matrix: HIGH/HIGH is P1; one HIGH with a MEDIUM is P2; HIGH with LOW, or MEDIUM/MEDIUM, or LOW with HIGH is P3; everything else is P4. There is no priority field in any request.
- The matrix is a pure function of the two enums (`Priority.of`), so it is the same everywhere it is needed and trivially testable (all nine combinations).
- Changing impact or urgency through `update` re-derives the priority; when the priority actually changes, the SLA due dates restart from the moment the incident was opened (ADR-032), and the audit entry says `P3 -> P1`.
- A priority is never edited directly, so the audit trail always explains where it came from: the incident's own impact and urgency.

## Consequences
- Positive: a consistent queue, no priority inflation by typing, a clock from the first second.
- Negative: a person can still overstate impact or urgency; that is visible in the audit trail and correctable by staff through `update`. Finer rules (a VIP, a customer-facing service) would need a richer model than a 3x3 matrix.
