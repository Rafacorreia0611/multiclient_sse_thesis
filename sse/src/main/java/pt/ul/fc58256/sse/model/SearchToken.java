package pt.ul.fc58256.sse.model;

import java.io.Serializable;

public record SearchToken(String token_ws, String token_w, int searchCounter) implements Serializable {

    public SearchToken {
        if (token_ws == null || token_w == null || searchCounter < 0) {
             throw new IllegalArgumentException("token_ws and token_w cannot be null and searchCounter cannot be negative");
        }
    }
}
