package com.thinklab.domain.exception;

/**
 * Domain Exception: a {@code REQUESTER} tried to do something only staff may do (ADR-031). A requester can file incidents, read their own
 * and comment on them; working an incident (assign, acknowledge, resolve...) and reading its audit trail are staff actions.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class IncidentAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-INC-00403";

    public IncidentAccessDeniedException(String operation) {
        super(ERROR_CODE, "A requester cannot " + operation + ": that is a staff action.");
    }
}
