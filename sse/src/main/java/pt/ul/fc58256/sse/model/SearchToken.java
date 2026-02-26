package pt.ul.fc58256.sse.model;

import java.io.Serializable;

public record SearchToken(String token_ws, String token_w) implements Serializable {

    public SearchToken {
        if (token_ws == null || token_w == null) {
            throw new IllegalArgumentException("token_ws or token_w cannot be null");
        }
    }
}
