package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class IncidentIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    private MongoCollection<Document> collectionIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("incidents")).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates the five incident indexes in the database named by mongodb.uri, each matching a real query")
    void createsIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "tenant_inc");
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new IncidentIndexInitializer(client, "mongodb://mongo:27017/tenant_inc").onApplicationEvent(startup);

        ArgumentCaptor<Document> keys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection, times(5)).createIndex(keys.capture(), options.capture());
        assertEquals(List.of(
                new Document("organisationId", 1).append("status", 1).append("priority", 1),
                new Document("organisationId", 1).append("assigneeId", 1),
                new Document("organisationId", 1).append("requesterId", 1),
                new Document("organisationId", 1).append("affectedAssetIds", 1),
                new Document("organisationId", 1).append("idempotencyKey", 1)), keys.getAllValues());
        assertTrue(options.getAllValues().get(4).isUnique());
        assertEquals(new Document("idempotencyKey", new Document("$type", "string")), options.getAllValues().get(4).getPartialFilterExpression());
        assertEquals(List.of(IncidentIndexInitializer.QUEUE_INDEX, IncidentIndexInitializer.ASSIGNEE_INDEX,
                IncidentIndexInitializer.REQUESTER_INDEX, IncidentIndexInitializer.ASSET_INDEX, IncidentIndexInitializer.IDEMPOTENCY_INDEX), options.getAllValues().stream().map(IndexOptions::getName).toList());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, IncidentMongoRepositoryAdapter.DEFAULT_DATABASE);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new IncidentIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(collection, times(5)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "inc_db");
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected")))
                .thenReturn(Mono.just("ok"));

        assertDoesNotThrow(() -> new IncidentIndexInitializer(client, "mongodb://mongo:27017/inc_db", Duration.ofSeconds(1)).onApplicationEvent(startup));
        verify(collection, times(5)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new IncidentIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new IncidentIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new IncidentIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
