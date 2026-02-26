package pt.ul.fc58256.sse.model;

import java.io.Serializable;

public record UpdateToken(String address, UpdateTuple updateTuple, String token_w) implements Serializable {
    
    public UpdateToken {
        if (address == null || updateTuple == null || token_w == null) {
            throw new IllegalArgumentException("address, updateTuple or token_w cannot be null");
        }
    }
}
