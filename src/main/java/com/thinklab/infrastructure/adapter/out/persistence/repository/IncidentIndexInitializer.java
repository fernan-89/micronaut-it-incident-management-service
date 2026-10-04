package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the compound indexes of the {@code incidents} collection at startup, each matching a query the adapter really runs: the work
 * queue ({@code organisationId, status, priority}), "my incidents" ({@code organisationId, assigneeId}), a requester's own incidents
 * ({@code organisationId, requesterId}) and "what happened to this asset" ({@code organisationId, affectedAssetIds}, multikey). The
 * adapter uses the driver directly, so the kit's generic {@code MongoIndexInitializer} does not see it.
 *
 * <p>Fail-open: {@code createIndex} is idempotent; a failure is logged and the application still starts. Turn it off with
 * {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class IncidentIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String QUEUE_INDEX = "organisationId_1_status_1_priority_1";
    static final String ASSIGNEE_INDEX = "organisationId_1_assigneeId_1";
    static final String REQUESTER_INDEX = "organisationId_1_requesterId_1";
    static final String ASSET_INDEX = "organisationId_1_affectedAssetIds_1";

    private static final Logger log = LoggerFactory.getLogger(IncidentIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public IncidentIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    IncidentIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : IncidentMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensureIndex(QUEUE_INDEX, new Document("organisationId", 1).append("status", 1).append("priority", 1));
        ensureIndex(ASSIGNEE_INDEX, new Document("organisationId", 1).append("assigneeId", 1));
        ensureIndex(REQUESTER_INDEX, new Document("organisationId", 1).append("requesterId", 1));
        ensureIndex(ASSET_INDEX, new Document("organisationId", 1).append("affectedAssetIds", 1));
    }

    private void ensureIndex(String indexName, Document keys) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(IncidentMongoRepositoryAdapter.COLLECTION_NAME)
                    .createIndex(keys, new IndexOptions().name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, IncidentMongoRepositoryAdapter.COLLECTION_NAME);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, IncidentMongoRepositoryAdapter.COLLECTION_NAME, e.getMessage());
        }
    }
}
