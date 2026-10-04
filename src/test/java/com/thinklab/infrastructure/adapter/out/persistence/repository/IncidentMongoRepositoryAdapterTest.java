package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.IncidentNotFoundException;
import com.thinklab.domain.exception.InvalidIncidentStatusException;
import com.thinklab.domain.model.Incident;
import com.thinklab.domain.model.Incident.Comment;
import com.thinklab.domain.model.Incident.Impact;
import com.thinklab.domain.model.Incident.IncidentStatus;
import com.thinklab.domain.model.Incident.Priority;
import com.thinklab.domain.model.Incident.SlaTargets;
import com.thinklab.domain.model.Incident.Urgency;
import com.thinklab.domain.repository.IncidentRepository.Filter;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.IncidentDocument.IncidentPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class IncidentMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<IncidentDocument> mongoCollection;

    private IncidentMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private Incident incident;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_it_incident_management_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("incidents", IncidentDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new IncidentMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_it_incident_management_db");
        organisationId = UUID.randomUUID();
        incident = Incident.createNew(UUID.randomUUID(), organisationId, UUID.randomUUID(), "Switch down", "No link", Impact.MEDIUM, Urgency.MEDIUM,
                Set.of(UUID.randomUUID()), null, new SlaTargets(Duration.ofMinutes(30), Duration.ofHours(8)), "op-1");
    }

    private FindPublisher<IncidentDocument> finds(Incident... found) {
        FindPublisher<IncidentDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        lenient().when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<IncidentDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(IncidentPersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("the database falls back to the service default when the URI has none")
    void databaseFallback() {
        IncidentMongoRepositoryAdapter fallback = new IncidentMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(IncidentDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallback.create(incident)).expectNext(incident).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(IncidentDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(incident)).expectNext(incident).verifyComplete();
    }

    @Test
    @DisplayName("findById is scoped to the organisation and maps the document back to the aggregate")
    void findById() {
        finds(incident);

        StepVerifier.create(adapter.findById(incident.getId(), organisationId))
                .assertNext(found -> assertEquals(incident.getId(), found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAll applies every optional filter, and the open-only exclusion")
    void findAll() {
        finds(incident);
        UUID anyId = UUID.randomUUID();

        StepVerifier.create(adapter.findAll(organisationId, new Filter(IncidentStatus.NEW, Priority.P3, anyId, anyId, anyId, true))).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(organisationId, new Filter(null, null, null, null, null, false))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, org.mockito.Mockito.times(2)).find(filter.capture());
        String full = filter.getAllValues().get(0).toString();
        assertTrue(full.contains("priority") && full.contains("assigneeId") && full.contains("affectedAssetIds") && full.contains("requesterId") && full.contains("CLOSED"));
        String bare = filter.getAllValues().get(1).toString();
        assertTrue(bare.contains("organisationId") && !bare.contains("priority") && !bare.contains("CLOSED"));
    }

    @Test
    @DisplayName("save is a guarded write: it needs the organisation and the status the incident was loaded in")
    void saveIsGuarded() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        var entry = incident.acknowledge("op-1");

        StepVerifier.create(adapter.save(incident, IncidentStatus.NEW, entry)).verifyComplete();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).updateOne(guard.capture(), any(Bson.class));
        assertTrue(guard.getValue().toString().contains("organisationId") && guard.getValue().toString().contains("NEW"));
    }

    @Test
    @DisplayName("when the guard matches nothing (someone moved the incident first) the answer is a 409-style conflict")
    void saveLosesTheRace() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        var entry = incident.acknowledge("op-1");

        StepVerifier.create(adapter.save(incident, IncidentStatus.NEW, entry)).expectError(InvalidIncidentStatusException.class).verify();
    }

    @Test
    @DisplayName("addComment pushes the comment and the audit entry, scoped to the organisation; an unknown incident is not found")
    void addComment() {
        var comment = new Comment(UUID.randomUUID(), "op-1", "Checked the port", false, null);
        var entry = incident.addComment(comment, "op-1");
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.addComment(incident.getId(), organisationId, comment, entry)).verifyComplete();
        StepVerifier.create(adapter.addComment(incident.getId(), organisationId, comment, entry)).expectError(IncidentNotFoundException.class).verify();
    }
}
