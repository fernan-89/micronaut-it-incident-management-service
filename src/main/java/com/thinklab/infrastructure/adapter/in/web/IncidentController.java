package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.AssignIncidentRequest;
import com.thinklab.application.dto.request.HoldIncidentRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiateIncidentRequest;
import com.thinklab.application.dto.request.ReopenIncidentRequest;
import com.thinklab.application.dto.request.ResolveIncidentRequest;
import com.thinklab.application.dto.request.UpdateIncidentRequest;
import com.thinklab.application.dto.response.IncidentAuditEntryResponse;
import com.thinklab.application.dto.response.IncidentResponse;
import com.thinklab.application.usecase.AssignIncidentUseCase;
import com.thinklab.application.usecase.ControlIncidentUseCase;
import com.thinklab.application.usecase.HoldIncidentUseCase;
import com.thinklab.application.usecase.InitiateCommentUseCase;
import com.thinklab.application.usecase.InitiateIncidentUseCase;
import com.thinklab.application.usecase.ReopenIncidentUseCase;
import com.thinklab.application.usecase.ResolveIncidentUseCase;
import com.thinklab.application.usecase.RetrieveIncidentAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveIncidentUseCase;
import com.thinklab.application.usecase.RetrieveIncidentsUseCase;
import com.thinklab.application.usecase.UpdateIncidentUseCase;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-incident-management} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Incident} is the Control Record. Every route follows
 * {@code /it-incident-management/v1/{control-record-id}/{behavior-qualifier}}. There is no {@code DELETE}: {@code control/cancel} and
 * {@code control/close} are terminal, soft status transitions.
 *
 * <p><b>Tenant on every route:</b> {@code X-Tenant-Id} is mandatory everywhere, and every lookup is scoped to it (another tenant's
 * incident is a 404).
 *
 * <p><b>Self-service scoping (ADR-031):</b> {@code X-Role}, set by the kit's {@code SecurityFilter} from the verified token when security
 * is on (client-supplied and trusted only when security is off, like every other header here), narrows a {@code REQUESTER} to their own
 * incidents, hides internal comments, and refuses the staff actions (ERR-INC-00403).
 */
@Controller("/it-incident-management/v1")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateIncidentUseCase initiateIncidentUseCase;
    private final RetrieveIncidentUseCase retrieveIncidentUseCase;
    private final RetrieveIncidentsUseCase retrieveIncidentsUseCase;
    private final UpdateIncidentUseCase updateIncidentUseCase;
    private final AssignIncidentUseCase assignIncidentUseCase;
    private final ControlIncidentUseCase controlIncidentUseCase;
    private final HoldIncidentUseCase holdIncidentUseCase;
    private final ResolveIncidentUseCase resolveIncidentUseCase;
    private final ReopenIncidentUseCase reopenIncidentUseCase;
    private final InitiateCommentUseCase initiateCommentUseCase;
    private final RetrieveIncidentAuditLogUseCase retrieveIncidentAuditLogUseCase;

    public IncidentController(
            InitiateIncidentUseCase initiateIncidentUseCase,
            RetrieveIncidentUseCase retrieveIncidentUseCase,
            RetrieveIncidentsUseCase retrieveIncidentsUseCase,
            UpdateIncidentUseCase updateIncidentUseCase,
            AssignIncidentUseCase assignIncidentUseCase,
            ControlIncidentUseCase controlIncidentUseCase,
            HoldIncidentUseCase holdIncidentUseCase,
            ResolveIncidentUseCase resolveIncidentUseCase,
            ReopenIncidentUseCase reopenIncidentUseCase,
            InitiateCommentUseCase initiateCommentUseCase,
            RetrieveIncidentAuditLogUseCase retrieveIncidentAuditLogUseCase
    ) {
        this.initiateIncidentUseCase = initiateIncidentUseCase;
        this.retrieveIncidentUseCase = retrieveIncidentUseCase;
        this.retrieveIncidentsUseCase = retrieveIncidentsUseCase;
        this.updateIncidentUseCase = updateIncidentUseCase;
        this.assignIncidentUseCase = assignIncidentUseCase;
        this.controlIncidentUseCase = controlIncidentUseCase;
        this.holdIncidentUseCase = holdIncidentUseCase;
        this.resolveIncidentUseCase = resolveIncidentUseCase;
        this.reopenIncidentUseCase = reopenIncidentUseCase;
        this.initiateCommentUseCase = initiateCommentUseCase;
        this.retrieveIncidentAuditLogUseCase = retrieveIncidentAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Opens a new Incident, by staff or self-service. */
    @Post("/initiate")
    public Mono<HttpResponse<IncidentResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateIncidentRequest request
    ) {
        log.info("[ACTION: INITIATE_INCIDENT] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiateIncidentUseCase.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single Incident of the tenant. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<IncidentResponse>> retrieveById(
            @PathVariable UUID id,
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role
    ) {
        log.info("[ACTION: RETRIEVE_INCIDENT] Received request to get Incident by ID: {}", id);

        return Mono.defer(() -> retrieveIncidentUseCase.execute(id, UUID.fromString(tenantId), executor, role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists the tenant's Incidents, filterable; {@code openOnly} leaves out the finished ones. */
    @Get("/retrieve")
    public Mono<List<IncidentResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @QueryValue @Nullable IncidentStatus status,
            @QueryValue @Nullable Priority priority,
            @QueryValue @Nullable UUID assigneeId,
            @QueryValue @Nullable UUID assetId,
            @QueryValue(defaultValue = "false") boolean openOnly
    ) {
        log.info("[ACTION: RETRIEVE_INCIDENTS] Received request to list Incidents for organisation: {} status: {} priority: {}", tenantId, status, priority);

        return Mono.defer(() -> retrieveIncidentsUseCase
                .execute(UUID.fromString(tenantId), status, priority, assigneeId, assetId, openOnly, executor, role).collectList());
    }

    /** Behavior Qualifier: {@code update}. Updates the details; a change of impact or urgency re-derives the priority. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateIncidentRequest request
    ) {
        return Mono.defer(() -> updateIncidentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code assignment/update}. Names who works the incident. */
    @Put("/{id}/assignment/update")
    public Mono<HttpResponse<Void>> assign(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid AssignIncidentRequest request
    ) {
        return Mono.defer(() -> assignIncidentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/acknowledge}. NEW -&gt; ACKNOWLEDGED. */
    @Put("/{id}/control/acknowledge")
    public Mono<HttpResponse<Void>> controlAcknowledge(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                       @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlIncidentUseCase.Action.ACKNOWLEDGE, executor, role);
    }

    /** Behavior Qualifier: {@code control/start}. ACKNOWLEDGED -&gt; IN_PROGRESS. */
    @Put("/{id}/control/start")
    public Mono<HttpResponse<Void>> controlStart(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                 @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlIncidentUseCase.Action.START, executor, role);
    }

    /** Behavior Qualifier: {@code control/hold}. IN_PROGRESS -&gt; ON_HOLD; needs a reason. */
    @Put("/{id}/control/hold")
    public Mono<HttpResponse<Void>> controlHold(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid HoldIncidentRequest request
    ) {
        return Mono.defer(() -> holdIncidentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/resume}. ON_HOLD -&gt; IN_PROGRESS. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> controlResume(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                  @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlIncidentUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code control/resolve}. ACKNOWLEDGED or IN_PROGRESS -&gt; RESOLVED; needs a resolution code and notes. */
    @Put("/{id}/control/resolve")
    public Mono<HttpResponse<Void>> controlResolve(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ResolveIncidentRequest request
    ) {
        return Mono.defer(() -> resolveIncidentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    @Put("/{id}/control/close")
    public Mono<HttpResponse<Void>> controlClose(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                 @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlIncidentUseCase.Action.CLOSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; IN_PROGRESS; needs a reason. */
    @Put("/{id}/control/reopen")
    public Mono<HttpResponse<Void>> controlReopen(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReopenIncidentRequest request
    ) {
        return Mono.defer(() -> reopenIncidentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/cancel}. Terminal, replaces DELETE. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                  @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlIncidentUseCase.Action.CANCEL, executor, role);
    }

    /** Behavior Qualifier: {@code comment/initiate}. Public, or internal for staff. */
    @Post("/{id}/comment/initiate")
    public Mono<HttpResponse<Void>> initiateComment(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateCommentRequest request
    ) {
        return Mono.defer(() -> initiateCommentUseCase.execute(id, UUID.fromString(tenantId), request, executor, role))
                .thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the Incident (staff only). */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<IncidentAuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                                   @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveIncidentAuditLogUseCase.execute(id, UUID.fromString(tenantId), role));
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlIncidentUseCase.Action action, String executor, String role) {
        log.info("[ACTION: CONTROL_INCIDENT] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return Mono.defer(() -> controlIncidentUseCase.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
