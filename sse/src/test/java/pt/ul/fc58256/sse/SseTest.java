package pt.ul.fc58256.sse;

import java.util.List;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pt.ul.fc58256.sse.crypto.SseKeys;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateOp;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

class SseTest {

    @Test
    @DisplayName("Old search token only sees updates that existed when it was generated")
    void staleTokenAfterUpdateOnlySeesFirstBatch() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        State initial = server.getState("update");
        server.updateQuery(SseClient.generateUpdateToken(key, initial, "keyword1", "doc1", UpdateOp.ADD, 0));

        SearchToken staleToken = SseClient.generateSearchToken(key, server.getState("search"), "keyword1");
        List<String> firstResult = SseClient.extractAddedDocIds(server.searchQuery(staleToken));
        assertEquals(List.of("doc1"), firstResult);

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc2", UpdateOp.ADD, 0));
        List<String> staleResultAfterSecondUpdate = SseClient.extractAddedDocIds(server.searchQuery(staleToken));
        assertEquals(List.of("doc1"), staleResultAfterSecondUpdate);

        SearchToken freshToken = SseClient.generateSearchToken(key, server.getState("search"), "keyword1");
        List<String> freshResult = SseClient.extractAddedDocIds(server.searchQuery(freshToken));
        assertEquals(List.of("doc1", "doc2"), freshResult);
    }

    @Test
    @DisplayName("Keyword isolation: searches only return docs for their keyword")
    void keywordIsolation() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword2", "doc2", UpdateOp.ADD, 0));

        List<String> keyword1Docs = searchDocs(server, key, "keyword1");
        List<String> keyword2Docs = searchDocs(server, key, "keyword2");

        assertEquals(List.of("doc1"), keyword1Docs);
        assertEquals(List.of("doc2"), keyword2Docs);
    }

    @Test
    @DisplayName("Searching an unknown keyword returns an empty list")
    void searchUnknownKeywordReturnsEmpty() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        SearchToken token = SseClient.generateSearchToken(key, server.getState("search"), "unknown-keyword");
        List<String> docs = SseClient.extractAddedDocIds(server.searchQuery(token));

        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("Deleting a non-existing doc behaves like a no operation")
    void deleteNonExistingDocIsNoOp() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "ghost-doc", UpdateOp.DEL, 0));

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs);
    }

    @Test
    @DisplayName("ADD-DEL-ADD for same doc ends with doc present")
    void addDeleteAddSameDocFinalStateIsPresent() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.DEL, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs);
    }

    @Test
    @DisplayName("Duplicate ADD of same doc is idempotent in extracted docs")
    void duplicateAddSameDocIsIdempotentInExtractedDocs() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs);
    }

    @Test
    @DisplayName("Address collision in update query throws")
    void updateAddressCollisionThrows() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        State sameState = server.getState("update");
        UpdateToken first = SseClient.generateUpdateToken(key, sameState, "keyword1", "doc1", UpdateOp.ADD, 0);
        UpdateToken second = SseClient.generateUpdateToken(key, sameState, "keyword1", "doc2", UpdateOp.ADD, 0);

        server.updateQuery(first);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> server.updateQuery(second));
        assertEquals("Address already exists in the inverted index", thrown.getMessage());
    }

    @Test
    @DisplayName("Concurrent clients recover from update collision using retry offset")
    void multiClientConcurrentUpdateWithRetryOffset() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        // Simulate two clients reading the same state concurrently.
        State sharedState = server.getState("update");
        UpdateToken client1Update = SseClient.generateUpdateToken(key, sharedState, "keyword-concurrent", "docA", UpdateOp.ADD, 0);
        UpdateToken client2Update = SseClient.generateUpdateToken(key, sharedState, "keyword-concurrent", "docB", UpdateOp.ADD, 0);

        server.updateQuery(client1Update);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> server.updateQuery(client2Update));
        assertEquals("Address already exists in the inverted index", thrown.getMessage());

        // Client 2 retries with offset=1 (next address slot in the same epoch).
        UpdateToken client2Retry = SseClient.generateUpdateToken(key, sharedState, "keyword-concurrent", "docB", UpdateOp.ADD, 1);
        server.updateQuery(client2Retry);

        List<String> docs = searchDocs(server, key, "keyword-concurrent");
        assertEquals(List.of("docA", "docB"), docs);
    }

    @Test
    @DisplayName("Search token generated with wrong key cannot recover docs")
    void wrongKeyTokenCannotRecoverDocs() {
        SseServer server = new SseServer();
        SecretKey correctKey = server.getTokenGenKey();
        SecretKey wrongKey = new SseKeys().getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(correctKey, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0));

        SearchToken wrongSearchToken = SseClient.generateSearchToken(wrongKey, server.getState("search"), "keyword1");
        List<String> docs = SseClient.extractAddedDocIds(server.searchQuery(wrongSearchToken));

        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("Interleaved updates across multiple keywords keep independent state")
    void multipleKeywordsWithInterleavedUpdates() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "kw1", "a1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "kw2", "b1", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "kw1", "a2", UpdateOp.ADD, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "kw2", "b1", UpdateOp.DEL, 0));
        server.updateQuery(SseClient.generateUpdateToken(key, server.getState("update"), "kw2", "b2", UpdateOp.ADD, 0));

        List<String> kw1Docs = searchDocs(server, key, "kw1");
        List<String> kw2Docs = searchDocs(server, key, "kw2");

        assertEquals(List.of("a1", "a2"), kw1Docs);
        assertEquals(List.of("b2"), kw2Docs);
    }

    private List<String> searchDocs(SseServer server, SecretKey key, String keyword) {
        SearchToken token = SseClient.generateSearchToken(key, server.getState("search"), keyword);
        List<UpdateTuple> updates = server.searchQuery(token);
        return SseClient.extractAddedDocIds(updates);
    }
}
