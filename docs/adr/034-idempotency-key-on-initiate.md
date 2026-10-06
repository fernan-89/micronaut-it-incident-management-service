# ADR-034: An Idempotency Key on Initiate, so a Retry Never Opens a Second Incident

## Status
Accepted

## Context
A caller that opens incidents on its own (the alerting service, ADR-030 there) can be interrupted after the incident exists and before it learns its id, or can run on several instances at once. Retrying `initiate` then opens a duplicate. A live run found it: four simultaneous evaluations opened three incidents for two outages.

## Decision
- `initiate` takes an optional `idempotencyKey` (at most 100 characters). **Staff only**: for a REQUESTER it is ignored, because a key would otherwise let a requester read back someone else's incident by guessing it. A blank key is no key.
- A repeated key (per organisation) answers with **the incident the first call opened**, exactly as the first call answered (201, same body); nothing is created and no sovereign id is spent.
- The guarantee is atomic: the key is stored on the incident and a **partial unique index on `(organisationId, idempotencyKey)`** (only where the key is a string) makes the second insert lose, even from two instances at once; the loser reads and returns the first incident.
- The key is not part of the incident aggregate and never leaves the service: it is not in any response, audit entry or event. Another organisation using the same key opens its own incident.

## Consequences
- Positive: an interrupted or concurrent caller can retry safely; the change is additive (a caller that sends no key behaves as before).
- Negative: the key is kept as long as the incident; a caller that reuses a key for a different incident gets the old one back, so keys must name the thing (an alert id), not a time.
