package pt.ul.fc58256.sse;

import java.util.List;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pt.ul.fc58256.sse.model.EncryptedUpdateTuple;
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
        PreparedUpdate firstUpdate = prepareUpdate(key, initial, "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(firstUpdate.token(), firstUpdate.updateTupleKey());

        SearchToken staleToken = SseClient.generateSearchToken(key, server.getState("search"), "keyword1");
        List<String> firstResult = SseClient.extractAddedDocIds(server.searchQuery(staleToken));
        assertEquals(List.of("doc1"), firstResult.stream().sorted().toList());

        PreparedUpdate secondUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "doc2", UpdateOp.ADD, 0);
        server.updateQuery(secondUpdate.token(), secondUpdate.updateTupleKey());
        List<String> staleResultAfterSecondUpdate = SseClient.extractAddedDocIds(server.searchQuery(staleToken));
        assertEquals(List.of("doc1"), staleResultAfterSecondUpdate.stream().sorted().toList());

        SearchToken freshToken = SseClient.generateSearchToken(key, server.getState("search"), "keyword1");
        List<String> freshResult = SseClient.extractAddedDocIds(server.searchQuery(freshToken));
        assertEquals(List.of("doc1", "doc2"), freshResult.stream().sorted().toList());
    }

    @Test
    @DisplayName("Keyword isolation: searches only return docs for their keyword")
    void keywordIsolation() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        PreparedUpdate keyword1Update = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(keyword1Update.token(), keyword1Update.updateTupleKey());
        PreparedUpdate keyword2Update = prepareUpdate(key, server.getState("update"), "keyword2", "doc2", UpdateOp.ADD, 0);
        server.updateQuery(keyword2Update.token(), keyword2Update.updateTupleKey());

        List<String> keyword1Docs = searchDocs(server, key, "keyword1");
        List<String> keyword2Docs = searchDocs(server, key, "keyword2");

        assertEquals(List.of("doc1"), keyword1Docs.stream().sorted().toList());
        assertEquals(List.of("doc2"), keyword2Docs.stream().sorted().toList());
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

        PreparedUpdate addUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(addUpdate.token(), addUpdate.updateTupleKey());
        PreparedUpdate deleteUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "ghost-doc", UpdateOp.DEL, 0);
        server.updateQuery(deleteUpdate.token(), deleteUpdate.updateTupleKey());

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs.stream().sorted().toList());
    }

    @Test
    @DisplayName("ADD-DEL-ADD for same doc ends with doc present")
    void addDeleteAddSameDocFinalStateIsPresent() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        PreparedUpdate addUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(addUpdate.token(), addUpdate.updateTupleKey());
        PreparedUpdate deleteUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.DEL, 0);
        server.updateQuery(deleteUpdate.token(), deleteUpdate.updateTupleKey());
        PreparedUpdate reAddUpdate = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(reAddUpdate.token(), reAddUpdate.updateTupleKey());

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs.stream().sorted().toList());
    }

    @Test
    @DisplayName("Duplicate ADD of same doc is idempotent in extracted docs")
    void duplicateAddSameDocIsIdempotentInExtractedDocs() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        PreparedUpdate firstAdd = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(firstAdd.token(), firstAdd.updateTupleKey());
        PreparedUpdate secondAdd = prepareUpdate(key, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(secondAdd.token(), secondAdd.updateTupleKey());

        List<String> docs = searchDocs(server, key, "keyword1");
        assertEquals(List.of("doc1"), docs.stream().sorted().toList());
    }

    @Test
    @DisplayName("Address collision in update query throws")
    void updateAddressCollisionThrows() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        State sameState = server.getState("update");
        PreparedUpdate first = prepareUpdate(key, sameState, "keyword1", "doc1", UpdateOp.ADD, 0);
        PreparedUpdate second = prepareUpdate(key, sameState, "keyword1", "doc2", UpdateOp.ADD, 0);

        server.updateQuery(first.token(), first.updateTupleKey());
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> server.updateQuery(second.token(), second.updateTupleKey()));
        assertEquals("Address already exists in the inverted index", thrown.getMessage());
    }

    @Test
    @DisplayName("Concurrent clients recover from update collision using retry offset")
    void multiClientConcurrentUpdateWithRetryOffset() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        // Simulate two clients reading the same state concurrently.
        State sharedState = server.getState("update");
        PreparedUpdate client1Update = prepareUpdate(key, sharedState, "keyword-concurrent", "docA", UpdateOp.ADD, 0);
        PreparedUpdate client2Update = prepareUpdate(key, sharedState, "keyword-concurrent", "docB", UpdateOp.ADD, 0);

        server.updateQuery(client1Update.token(), client1Update.updateTupleKey());
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> server.updateQuery(client2Update.token(), client2Update.updateTupleKey()));
        assertEquals("Address already exists in the inverted index", thrown.getMessage());

        // Client 2 retries with offset=1 (next address slot in the same epoch).
        PreparedUpdate client2Retry = prepareUpdate(key, sharedState, "keyword-concurrent", "docB", UpdateOp.ADD, 1);
        server.updateQuery(client2Retry.token(), client2Retry.updateTupleKey());

        List<String> docs = searchDocs(server, key, "keyword-concurrent");
        assertEquals(List.of("docA", "docB"), docs.stream().sorted().toList());
    }

    @Test
    @DisplayName("Search token generated with wrong key cannot recover docs")
    void wrongKeyTokenCannotRecoverDocs() {
        SseServer server = new SseServer();
        SecretKey correctKey = server.getTokenGenKey();
        SecretKey wrongKey = new SseServer().getTokenGenKey();

        PreparedUpdate correctUpdate = prepareUpdate(correctKey, server.getState("update"), "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(correctUpdate.token(), correctUpdate.updateTupleKey());

        SearchToken wrongSearchToken = SseClient.generateSearchToken(wrongKey, server.getState("search"), "keyword1");
        List<String> docs = SseClient.extractAddedDocIds(server.searchQuery(wrongSearchToken));

        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("Interleaved updates across multiple keywords keep independent state")
    void multipleKeywordsWithInterleavedUpdates() {
        SseServer server = new SseServer();
        SecretKey key = server.getTokenGenKey();

        PreparedUpdate kw1First = prepareUpdate(key, server.getState("update"), "kw1", "a1", UpdateOp.ADD, 0);
        server.updateQuery(kw1First.token(), kw1First.updateTupleKey());
        PreparedUpdate kw2First = prepareUpdate(key, server.getState("update"), "kw2", "b1", UpdateOp.ADD, 0);
        server.updateQuery(kw2First.token(), kw2First.updateTupleKey());
        PreparedUpdate kw1Second = prepareUpdate(key, server.getState("update"), "kw1", "a2", UpdateOp.ADD, 0);
        server.updateQuery(kw1Second.token(), kw1Second.updateTupleKey());
        PreparedUpdate kw2Delete = prepareUpdate(key, server.getState("update"), "kw2", "b1", UpdateOp.DEL, 0);
        server.updateQuery(kw2Delete.token(), kw2Delete.updateTupleKey());
        PreparedUpdate kw2Second = prepareUpdate(key, server.getState("update"), "kw2", "b2", UpdateOp.ADD, 0);
        server.updateQuery(kw2Second.token(), kw2Second.updateTupleKey());

        List<String> kw1Docs = searchDocs(server, key, "kw1");
        List<String> kw2Docs = searchDocs(server, key, "kw2");

        assertEquals(List.of("a1", "a2"), kw1Docs.stream().sorted().toList());
        assertEquals(List.of("b2"), kw2Docs.stream().sorted().toList());
    }

    private List<String> searchDocs(SseServer server, SecretKey key, String keyword) {
        SearchToken token = SseClient.generateSearchToken(key, server.getState("search"), keyword);
        return SseClient.extractAddedDocIds(server.searchQuery(token));
    }

    private PreparedUpdate prepareUpdate(SecretKey tokenGenKey, State state, String keyword, String docId, UpdateOp op, int retryOffset) {
        SecretKey updateTupleKey = SseClient.generateTupleSecretKey();
        EncryptedUpdateTuple encryptedTuple;
        try {
            encryptedTuple = SseClient.encryptUpdateTuple(updateTupleKey, SseClient.generateTupleIv(),new UpdateTuple(docId, op));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare encrypted update tuple", e);
        }

        UpdateToken updateToken = SseClient.generateUpdateToken(tokenGenKey, state, keyword, encryptedTuple, retryOffset);
        return new PreparedUpdate(updateToken, updateTupleKey);
    }

    private record PreparedUpdate(UpdateToken token, SecretKey updateTupleKey) {}
}
