package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.IncidentPersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Opening an incident under an idempotency key against the collection: the key is stored, and a duplicate key answers with the first incident. */
@SuppressWarnings("unchecked")
class IncidentIdempotentAdapterTest {

    private final UUID organisationId = UUID.randomUUID();
    private MongoCollection<IncidentDocument> collection;
    private IncidentMongoRepositoryAdapter adapter;
    private Incident incident;

    @BeforeEach
    void setUp() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_incident_management_db")).thenReturn(database);
        when(database.getCollection("incidents", IncidentDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
        adapter = new IncidentMongoRepositoryAdapter(client, "mongodb://localhost:27017/thinklab_it_incident_management_db");
        incident = Incident.createNew(UUID.randomUUID(), organisationId, UUID.randomUUID(), "t", "d", Impact.LOW, Urgency.LOW, null, null,
                new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8)), "op-1");
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(Incident... found) {
        FindPublisher<IncidentDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<IncidentDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(IncidentPersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("the key is stored with the incident, and the incident comes back")
    void createIdempotent() {
        when(collection.insertOne(any(IncidentDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.createIdempotent(incident, "alert-1")).expectNext(incident).verifyComplete();

        ArgumentCaptor<IncidentDocument> stored = ArgumentCaptor.forClass(IncidentDocument.class);
        verify(collection).insertOne(stored.capture());
        assertEquals("alert-1", stored.getValue().getIdempotencyKey());
        assertNull(IncidentPersistenceMapper.toDocument(incident).getIdempotencyKey());
    }

    @Test
    @DisplayName("losing the race on the key answers with the first incident; any other write error passes through; a vanished first incident is the original error")
    void duplicateKey() {
        when(collection.insertOne(any(IncidentDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)))
                .thenReturn(Mono.error(writeError(11000)));
        Incident first = Incident.createNew(UUID.randomUUID(), organisationId, UUID.randomUUID(), "first", "d", Impact.LOW, Urgency.LOW, null, null,
                new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8)), "op-1");
        finds(first);

        StepVerifier.create(adapter.createIdempotent(incident, "alert-1")).assertNext(found -> assertEquals(first.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.createIdempotent(incident, "alert-1")).expectError(MongoWriteException.class).verify();
        finds();
        StepVerifier.create(adapter.createIdempotent(incident, "alert-1")).expectError(MongoWriteException.class).verify();
    }

    @Test
    @DisplayName("the lookup by key is scoped to the organisation")
    void findByKey() {
        finds(incident);

        StepVerifier.create(adapter.findByIdempotencyKey(organisationId, "alert-1")).assertNext(found -> assertEquals(incident.getId(), found.getId())).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(filter.capture());
        assertTrue(filter.getValue().toString().contains("organisationId") && filter.getValue().toString().contains("idempotencyKey"));
    }
}
