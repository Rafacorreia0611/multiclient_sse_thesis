package pt.ul.fc58256.sse.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record State(Map<String, Integer> searchCounter, Map<String, Integer> updateCounter) implements Serializable {

    public State {
        if (searchCounter == null || updateCounter == null) {
            throw new IllegalArgumentException("Counters cannot be null");
        }
        searchCounter = Collections.unmodifiableMap(new HashMap<>(searchCounter));
        updateCounter = Collections.unmodifiableMap(new HashMap<>(updateCounter));
    }

    public State (Map<String, Integer> searchCounter) {
        this(searchCounter, Map.of());
    }
    
}
