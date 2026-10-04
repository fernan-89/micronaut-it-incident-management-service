# micronaut-it-incident-management-service

BIAN-aligned Service Domain **it-incident-management** (Control Record: `Incident`), port `8098`.

An unplanned interruption of an IT service: who reported it, what it affects, how urgent it is, who works it, and how it ended. The
first service of the ITSM core (Journey 12). An incident has a title, a description, an **impact** and an **urgency** (from which its
**priority** P1..P4 is derived, ADR-030), the assets it affects and the changes related to it, an assignee, comments, SLA targets and a
status that follows an ITIL-style lifecycle.

## What it guarantees, and what it does not

- **Priority is derived, never typed** (ADR-030): HIGH/HIGH is P1 ... LOW/LOW is P4. Changing impact or urgency re-derives it and restarts
  the SLA targets from the opening time.
- **SLA is stored as due dates and judged when read** (ADR-032): a response target (until acknowledged) and a resolution target (until
  resolved), each `PENDING`, `MET` or `BREACHED`. No scheduler. The clock does not pause on hold; a cancelled incident has no SLA.
  Targets come from `thinklab.incident.sla.response.P1..P4` and `resolution.P1..P4` (defaults: P1 15 min / 4 h, P2 30 min / 8 h,
  P3 4 h / 3 d, P4 24 h / 7 d).
- **Self-service without a second API** (ADR-031): a `REQUESTER` files on their own behalf, sees only their own incidents and never the
  internal notes, and cannot do any staff action (403 `ERR-INC-00403`).
- **No lost updates** (ADR-033): every change is one guarded write that also appends its audit entry; two people moving the same
  incident cannot both win (the loser gets 409 and retries).
- **Links are references** (ADR-033): affected assets and related changes are ids, not validated against the services that own them,
  so opening an incident never fails because another service is down.
- **Not here yet:** business-hours calendars, a pause on hold, breach alerts (Journey 14), problem records, a service catalog and
  knowledge base (the rest of Journey 12), events, and external ticketing connectors.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call and scopes it (another tenant's incident answers 404); `X-Executor` is mandatory (a user id for
a REQUESTER); `X-Role` is optional and, with platform security on, comes from the verified token.

### Incident - `/it-incident-management/v1`

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-incident-management/v1/initiate` `{"title":"Core switch down","description":"No link on floor 3","impact":"HIGH","urgency":"MEDIUM","requesterId":"<uuid>","affectedAssetIds":["<uuid>"],"relatedChangeIds":[]}` |
| retrieve | `GET /it-incident-management/v1/{id}/retrieve` |
| retrieve (collection) | `GET /it-incident-management/v1/retrieve?status=&priority=&assigneeId=&assetId=&openOnly=` |
| update | `PUT /it-incident-management/v1/{id}/update` (title, description, impact, urgency, links; not once resolved) |
| assignment/update | `PUT /it-incident-management/v1/{id}/assignment/update` `{"assigneeId":"<uuid>"}` |
| control/acknowledge | `PUT /it-incident-management/v1/{id}/control/acknowledge` (NEW -> ACKNOWLEDGED, stops the response clock) |
| control/start | `PUT /it-incident-management/v1/{id}/control/start` (ACKNOWLEDGED -> IN_PROGRESS) |
| control/hold | `PUT /it-incident-management/v1/{id}/control/hold` `{"reason":"Waiting for the vendor"}` (IN_PROGRESS -> ON_HOLD) |
| control/resume | `PUT /it-incident-management/v1/{id}/control/resume` (ON_HOLD -> IN_PROGRESS) |
| control/resolve | `PUT /it-incident-management/v1/{id}/control/resolve` `{"resolutionCode":"REPLACED","notes":"..."}` (ACKNOWLEDGED or IN_PROGRESS -> RESOLVED) |
| control/close | `PUT /it-incident-management/v1/{id}/control/close` (RESOLVED -> CLOSED, terminal) |
| control/reopen | `PUT /it-incident-management/v1/{id}/control/reopen` `{"reason":"Still failing"}` (RESOLVED -> IN_PROGRESS) |
| control/cancel | `PUT /it-incident-management/v1/{id}/control/cancel` (before it is resolved; terminal) |
| comment/initiate | `POST /it-incident-management/v1/{id}/comment/initiate` `{"text":"...","internal":false}` |
| audit-log/retrieve | `GET /it-incident-management/v1/{id}/audit-log/retrieve` (staff only) |

```text
NEW -> ACKNOWLEDGED -> IN_PROGRESS <-> ON_HOLD
            \              |
             +-----+-------+
                   v
               RESOLVED -> CLOSED (terminal)         RESOLVED --reopen--> IN_PROGRESS
NEW | ACKNOWLEDGED | IN_PROGRESS | ON_HOLD --cancel--> CANCELLED (terminal)
```

```bash
curl "http://localhost:8098/it-incident-management/v1/retrieve?openOnly=true&priority=P1" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: <userId>"
# [{"title":"Core switch down","priority":"P1","status":"IN_PROGRESS","response":{"dueAt":"...","state":"MET"},"resolution":{"dueAt":"...","state":"PENDING"},...}]
```

Do not put personal data in a title, a description or a comment: they are stored with the incident.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-INC-00403` | 403 | A REQUESTER tried a staff action (ADR-031) |
| `ERR-INC-00404` | 404 | Incident not found (another tenant's, or another requester's, answers the same) |
| `ERR-INC-00409` | 409 | Illegal transition, a change on a closed or cancelled incident, or the incident changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure (blank title, missing impact, a reason or notes required...) |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 priority derived from impact and urgency · 031 requester self-service scoping · 032 SLA computed when read · 033 links are references,
writes are guarded.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
