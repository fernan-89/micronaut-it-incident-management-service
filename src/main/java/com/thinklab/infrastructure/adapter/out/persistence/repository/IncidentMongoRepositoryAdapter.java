package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.exception.InvalidIncidentStatusException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.IncidentAuditEntry;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.repository.IncidentRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.CommentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.IncidentPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the Incident aggregate, raw reactive-streams driver. Every change is a single atomic
 * {@code $set}/{@code $push} that also appends the forensic audit entry, so state and ledger can never diverge, and every filter carries
 * the organisation: another tenant's incident is simply not found.
 */
@Singleton
public class IncidentMongoRepositoryAdapter implements IncidentRepository {

    private static final Logger log = LoggerFactory.getLogger(IncidentMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_incident_management_db";
    static final String COLLECTION_NAME = "incidents";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public IncidentMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<IncidentDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, IncidentDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Incident> create(Incident incident) {
        log.debug("[PERSISTENCE] Monolithic create for Incident Aggregate: {}", incident.getId());

        return Mono.from(getCollection().insertOne(IncidentPersistenceMapper.toDocument(incident))).map(result -> incident);
    }

    @Override
    public Mono<Incident> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(IncidentPersistenceMapper::toDomain);
    }

    @Override
    public Flux<Incident> findAll(UUID organisationId, Filter filter) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (filter.status() != null) {
            filters.add(Filters.eq(FIELD_STATUS, filter.status().name()));
        }
        if (filter.priority() != null) {
            filters.add(Filters.eq("priority", filter.priority().name()));
        }
        if (filter.assigneeId() != null) {
            filters.add(Filters.eq("assigneeId", filter.assigneeId()));
        }
        if (filter.assetId() != null) {
            filters.add(Filters.eq("affectedAssetIds", filter.assetId()));
        }
        if (filter.requesterId() != null) {
            filters.add(Filters.eq("requesterId", filter.requesterId()));
        }
        if (filter.openOnly()) {
            filters.add(Filters.nin(FIELD_STATUS, IncidentStatus.RESOLVED.name(), IncidentStatus.CLOSED.name(), IncidentStatus.CANCELLED.name()));
        }

        return Flux.from(getCollection().find(Filters.and(filters))).map(IncidentPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Incident incident, IncidentStatus expectedStatus, IncidentAuditEntry auditEntry) {
        // Guarded write: it only applies while the incident still has the status it had when it was loaded.
        Bson guard = Filters.and(Filters.eq(FIELD_ID, incident.getId()), Filters.eq(FIELD_ORGANISATION, incident.getOrganisationId()),
                Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("title", incident.getTitle()),
                Updates.set("description", incident.getDescription()),
                Updates.set("impact", incident.getImpact().name()),
                Updates.set("urgency", incident.getUrgency().name()),
                Updates.set("priority", incident.getPriority().name()),
                Updates.set(FIELD_STATUS, incident.getStatus().name()),
                Updates.set("assigneeId", incident.getAssigneeId()),
                Updates.set("affectedAssetIds", new ArrayList<>(incident.getAffectedAssetIds())),
                Updates.set("relatedChangeIds", new ArrayList<>(incident.getRelatedChangeIds())),
                Updates.set("holdReason", incident.getHoldReason()),
                Updates.set("resolutionCode", incident.getResolutionCode()),
                Updates.set("resolutionNotes", incident.getResolutionNotes()),
                Updates.set("reopenCount", incident.getReopenCount()),
                Updates.set("responseDueAt", incident.getResponseDueAt()),
                Updates.set("resolutionDueAt", incident.getResolutionDueAt()),
                Updates.set("acknowledgedAt", incident.getAcknowledgedAt()),
                Updates.set("resolvedAt", incident.getResolvedAt()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidIncidentStatusException("Incident was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty());
    }

    @Override
    public Mono<Void> addComment(UUID id, UUID organisationId, Comment comment, IncidentAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.push("comments", CommentDocument.fromDomain(comment)),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId)), update))
                .flatMap(result -> result.getMatchedCount() == 0 ? Mono.error(new IncidentNotFoundException(id)) : Mono.<Void>empty());
    }
}
