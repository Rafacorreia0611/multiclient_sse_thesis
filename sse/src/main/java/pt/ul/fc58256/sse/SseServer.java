package pt.ul.fc58256.sse;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import pt.ul.fc58256.sse.crypto.Prf;
import pt.ul.fc58256.sse.crypto.SseKeys;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

public class SseServer {
    
    private Map<String, Integer> searchCounter;
    private Map<String, Integer> updateCounter;
    private Map<String, List<String>> dbCache;
    private Map<String, Integer> nextSearchIndex;
    private Map<String, UpdateTuple> invertedIndex;
    private SseKeys keys;

    public SseServer() {
        this.searchCounter = new HashMap<>();
        this.updateCounter = new HashMap<>();
        this.dbCache = new HashMap<>();
        this.nextSearchIndex = new HashMap<>();
        this.invertedIndex = new HashMap<>();
        this.keys = new SseKeys();
    }

    // token_ws and token_w are Base64 encoded strings
    public List<UpdateTuple> searchQuery(SearchToken searchToken) {

        String token_ws = searchToken.token_ws();
        String token_w = searchToken.token_w();

        // Check if the search counter for the token_w is 0, if so, nextSearchIndex should be initialized to 1
        if (!searchCounter.containsKey(token_w)) {
            searchCounter.put(token_w, 0);
            nextSearchIndex.put(token_w, 1);
        }
        // Iterate over from the nextSearchIndex to the current updateCounter
        byte[] tokenWsBytes = Base64.getDecoder().decode(token_ws);
        int currentUpdateCounter = updateCounter.getOrDefault(token_w, 0);
        int nextIndex = nextSearchIndex.get(token_w);
        List<UpdateTuple> updates = new LinkedList<>();
        List<String> newAddresses = new LinkedList<>();
        for (int i = nextIndex; i <= currentUpdateCounter; i++) {
            // Get the address of the i-th update for token_ws from the inverted index
            byte[] address = Prf.prf(tokenWsBytes, Integer.toString(i));
            String addressBase64 = Base64.getEncoder().encodeToString(address);
            UpdateTuple update = invertedIndex.get(addressBase64);
            if (update != null) {
                updates.add(update);
                newAddresses.add(addressBase64);
            }
        }
        // Retrive the updates, where the address is keeped in the cache, and add them to the result list
        List<String> cachedAddresses = dbCache.getOrDefault(token_w, new LinkedList<>());
        if (!cachedAddresses.isEmpty()) {
            for (String address : cachedAddresses) {
                UpdateTuple update = invertedIndex.get(address);
                if (update != null) {
                    updates.add(update);
                }
            }
        }
        // Update the nextSearchIndex to the current updateCounter + 1
        nextSearchIndex.put(token_w, currentUpdateCounter + 1);
        // Update the cache with the new addresses
        cachedAddresses.addAll(newAddresses);
        dbCache.put(token_w, cachedAddresses);
        // Update the search counter for the token_w
        searchCounter.put(token_w, searchCounter.get(token_w) + 1);
        // Return the list of updates
        return updates;
    }

    public void updateQuery(UpdateToken updateToken) {

        String address = updateToken.address();
        UpdateTuple update = updateToken.updateTuple();
        String token_w = updateToken.token_w();
        
        // Update the inverted index with the new update
        if (invertedIndex.containsKey(address)) {
            throw new IllegalArgumentException("Address already exists in the inverted index");
        }
        invertedIndex.put(address, update);
        // Update the update counter for the token_w
        this.updateCounter.put(token_w, this.updateCounter.getOrDefault(token_w, 0) + 1);

    }

    public State getStateSearchToken() {
        return new State(searchCounter);
    }

    public State getStateUpdateToken() {
        return new State(searchCounter, updateCounter);
    }

    public SecretKey getTokenGenKey() {
        return keys.getTokenGenKey();
    }
}
