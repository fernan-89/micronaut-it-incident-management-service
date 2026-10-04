package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.IncidentRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for adding a Comment to an Incident (BIAN Behavior Qualifier: {@code comment/initiate}). A REQUESTER can comment only on their
 * own incident (anyone else's is a 404) and can never author an internal note: the field is forced to {@code false} for that role.
 */
@Singleton
public class InitiateCommentUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateCommentUseCase.class);

    private final HashServicePort hashServicePort;
    private final IncidentRepository incidentRepository;

    public InitiateCommentUseCase(HashServicePort hashServicePort, IncidentRepository incidentRepository) {
        this.hashServicePort = hashServicePort;
        this.incidentRepository = incidentRepository;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, InitiateCommentRequest request, String executor, String role) {
        log.info("[USE CASE] Adding a comment to Incident ID: {}", id);

        boolean requester = IncidentWorkflow.REQUESTER_ROLE.equals(role);
        boolean internal = !requester && request.internal();
        return incidentRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new IncidentNotFoundException(id)))
                .filter(incident -> !requester || incident.getRequesterId().equals(UUID.fromString(executor)))
                .switchIfEmpty(Mono.error(new IncidentNotFoundException(id)))
                .flatMap(incident -> hashServicePort.generateSovereignId("incident-comment-creation")
                        .flatMap(commentId -> {
                            Comment comment = new Comment(commentId, executor, request.text(), internal, Instant.now());
                            var entry = incident.addComment(comment, executor);
                            return incidentRepository.addComment(id, organisationId, comment, entry);
                        }));
    }
}
