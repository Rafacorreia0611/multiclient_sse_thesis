package pt.ul.fc58256.sse;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import pt.ul.fc58256.sse.crypto.Prf;
import pt.ul.fc58256.sse.model.EncryptedUpdateTuple;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateToken;

public class SseServer {
    
    private Map<String, Integer> searchCounter;
    private Map<String, Integer> updateCounter;
    private Map<String, List<String>> dbCache;
    private Map<String, Integer> nextSearchIndex;
    private Map<String, EncryptedUpdateTuple> invertedIndex;
    private Map<String, SecretKey> updateTupleKeys;
    private final SecretKey tokenGenKey;

    public SseServer() {
        this.searchCounter = new HashMap<>();
        this.updateCounter = new HashMap<>();
        this.dbCache = new HashMap<>();
        this.nextSearchIndex = new HashMap<>();
        this.invertedIndex = new HashMap<>();
        this.updateTupleKeys = new HashMap<>();
        this.tokenGenKey = generateRandomHmacKey();
    }

    // token_ws and token_w are Base64 encoded strings
    public Map<EncryptedUpdateTuple, SecretKey> searchQuery(SearchToken searchToken) {

        String token_ws = searchToken.token_ws();
        String token_w = searchToken.token_w();

        // Check if the search counter for the token_w is 0, if so, nextSearchIndex should be initialized to 1
        if (!searchCounter.containsKey(token_w)) {
            searchCounter.put(token_w, 0);
            nextSearchIndex.put(token_w, 1);
        }
        Map<EncryptedUpdateTuple, SecretKey> ret = new LinkedHashMap<>();
        // Retrive the updates, where the address is keeped in the cache, and add them to the result list
        List<String> cachedAddresses = dbCache.getOrDefault(token_w, new LinkedList<>());
        if (!cachedAddresses.isEmpty()) {
            for (String address : cachedAddresses) {
                EncryptedUpdateTuple update = invertedIndex.get(address);
                SecretKey updateTupleKey = updateTupleKeys.get(address);
                if (update != null) {
                    ret.put(update, updateTupleKey);
                }
            }
        }
        if (searchToken.searchCounter() != searchCounter.get(token_w)) {
            return ret;
        }
        // Iterate over from the nextSearchIndex to the current updateCounter
        byte[] tokenWsBytes = Base64.getDecoder().decode(token_ws);
        int currentUpdateCounter = updateCounter.getOrDefault(token_w, 0);
        int nextIndex = nextSearchIndex.get(token_w);
        List<String> newAddresses = new LinkedList<>();
        for (int i = nextIndex; i <= currentUpdateCounter; i++) {
            // Get the address of the i-th update for token_ws from the inverted index
            byte[] address = Prf.prf(tokenWsBytes, Integer.toString(i));
            String addressBase64 = Base64.getEncoder().encodeToString(address);
            EncryptedUpdateTuple update = invertedIndex.get(addressBase64);
            if (update != null) {
                SecretKey updateTupleKey = updateTupleKeys.get(addressBase64);
                ret.put(update, updateTupleKey);
                newAddresses.add(addressBase64);
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
        return ret;
    }

    public void updateQuery(UpdateToken updateToken, SecretKey updateTupleKey) {

        String address = updateToken.address();
        EncryptedUpdateTuple encryptedTuple = updateToken.encryptedTuple();
        String token_w = updateToken.token_w();
        
        // Update the inverted index with the new update
        if (invertedIndex.containsKey(address)) {
            throw new IllegalArgumentException("Address already exists in the inverted index");
        }
        invertedIndex.put(address, encryptedTuple);
        updateTupleKeys.put(address, updateTupleKey);
        // Update the update counter for the token_w
        this.updateCounter.put(token_w, this.updateCounter.getOrDefault(token_w, 0) + 1);

    }

    public State getState(String op) {
        if (op == null || (!op.equalsIgnoreCase("search") && !op.equalsIgnoreCase("update"))) {
            throw new IllegalArgumentException("Operation must be either 'search' or 'update'");
        }
        if (op.equalsIgnoreCase("search")) {
            return new State(searchCounter);
        } else {
            return new State(searchCounter, updateCounter);
        }
    }

    public SecretKey getTokenGenKey() {
        return tokenGenKey;
    }

    private static SecretKey generateRandomHmacKey() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 not available in this JVM/provider", e);
        }
        keyGen.init(256);
        return keyGen.generateKey();
    }
}
