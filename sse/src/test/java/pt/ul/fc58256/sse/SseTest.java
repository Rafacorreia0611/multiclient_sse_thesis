package pt.ul.fc58256.sse;

import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateOp;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

class SseTest {
    
    @Test
    @DisplayName("Add and search functionality works correctly")
    void addAndSearch() {
        SseServer server = new SseServer();
        State initialState = server.getState("update");
        SecretKey key = server.getTokenGenKey();
        
        UpdateToken updateToken1 = SseClient.generateUpdateToken(key, initialState, "keyword1", "doc1", UpdateOp.ADD, 0);
        server.updateQuery(updateToken1);
        State stateAfterFirstUpdate = server.getState("search"); // Get the state after the first update to generate the search token
        SearchToken searchToken1 = SseClient.generateSearchToken(key, stateAfterFirstUpdate, "keyword1");
        List<UpdateTuple> searchResults1 = server.searchQuery(searchToken1);
        List<String> docIds1 = SseClient.extractAddedDocIds(searchResults1);
        // The first search should return doc1 as the only result
        assert docIds1.equals(List.of("doc1"));

        State stateAfterFirstSearch = server.getState("update"); // Get the state after the first search to generate the second update token
        UpdateToken updateToken2 = SseClient.generateUpdateToken(key, stateAfterFirstSearch, "keyword1", "doc2", UpdateOp.ADD, 0);
        server.updateQuery(updateToken2);
        List<UpdateTuple> searchResults2 = server.searchQuery(searchToken1);
        List<String> docIds2 = SseClient.extractAddedDocIds(searchResults2);
        // The second search should return only doc1, because we are using the same search token, which should not see updates that happened after it was generated aka forward privacy
        assert docIds2.equals(List.of("doc1"));

        State stateAfterSecondSearch = server.getState("update"); // Get the state after the second search to generate the third search token
        SearchToken searchToken2 = SseClient.generateSearchToken(key, stateAfterSecondSearch, "keyword1");
        List<UpdateTuple> searchResults3 = server.searchQuery(searchToken2);
        List<String> docIds3 = SseClient.extractAddedDocIds(searchResults3);
        // The third search should return both doc1 and doc2, because we are using a new search token, which should see all updates that happened before it was generated
        assert docIds3.equals(List.of("doc1", "doc2"));
    }
}
