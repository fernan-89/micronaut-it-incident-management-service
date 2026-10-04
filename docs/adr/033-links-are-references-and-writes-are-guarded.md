# ADR-033: Links Are References, and Every Change Is a Guarded Write

## Status
Accepted

## Context
An incident points at the assets it affects and the changes related to it. Validating each id against the owning service would make opening an incident - the most urgent thing a person does - depend on three other services being up. Separately, two people working the same incident could silently overwrite each other.

## Decision
- **Links are references.** `affectedAssetIds` and `relatedChangeIds` are stored as ids; this service calls no other service (only the hash registry for sovereign ids). An id that does not exist is not refused at creation. The web app resolves the links when it shows an incident (for example the blast radius of an affected asset from the topology graph).
- **Every change is a single guarded write.** The repository saves the state the aggregate reached together with its audit entry in one atomic update, and only while the incident still has the status it had when it was loaded. If someone else moved it in between, the write matches nothing and the caller gets 409 `ERR-INC-00409` (read again and retry) - never a lost update. Comments are an atomic `$push` and need no guard.
- Four compound indexes serve the real queries: the work queue `(organisationId, status, priority)`, `(organisationId, assigneeId)`, `(organisationId, requesterId)` and `(organisationId, affectedAssetIds)`.

## Consequences
- Positive: opening an incident never fails because another service is down; no lost updates; no partial writes.
- Negative: a mistyped asset id is accepted (a later consistency check or event-driven link validation could flag it); the guard is on the status, so two edits that do not change the status (for example two `assignment/update` calls at the same instant) are last-writer-wins, which is acceptable for a field that is meant to be overwritten.
