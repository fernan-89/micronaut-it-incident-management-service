# ADR-031: Requester Self-Service - Their Own Incidents Only, Never Internal Notes, Never Staff Actions

## Status
Accepted

## Context
People who are not IT staff must be able to report an incident and follow it. The platform already has a `REQUESTER` role with the same method envelope as an OPERATOR (kit), which on its own would let a requester work, resolve or cancel anyone's incident.

## Decision
- `X-Role` (set by the kit's `SecurityFilter` from the verified token when security is on; client-supplied and trusted only with security off, like every header here) selects the behaviour; every use case takes the role as an optional string, so the service never depends on RBAC being enabled.
- A **REQUESTER** can: open an incident (the requester is forced to the token-derived executor, whatever the body says), read **only their own** incidents (the collection query itself is filtered by requester; someone else's incident is a 404, indistinguishable from a missing one), and comment publicly on their own (an `internal` flag is ignored). They never see internal comments.
- A REQUESTER cannot do any **staff action**: update, assign, acknowledge, start, hold, resume, resolve, reopen, close, cancel, or read the audit trail. These answer **403 `ERR-INC-00403`**, before anything is read. (The work-order service this pattern comes from does not enforce this on its control routes; here a requester must not be able to close or cancel an incident, including their own.)
- Staff opening an incident for someone must name the requester.
- **Tenant on every route:** `X-Tenant-Id` is mandatory everywhere and every lookup is scoped to it; another tenant's incident is a 404.

## Consequences
- Positive: self-service without a second API; an unambiguous rule about who can move an incident.
- Negative: a requester cannot cancel or reopen their own incident through the API (they comment, and staff act); a header-only local sign-in that is not a user id cannot act as a REQUESTER (the executor must be a UUID for ownership).
