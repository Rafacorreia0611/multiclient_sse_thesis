package pt.ul.fc58256.sse.model;

import java.io.Serializable;

public record UpdateToken(String address, EncryptedUpdateTuple encryptedTuple, String token_w) implements Serializable {
    
    public UpdateToken {
        if (address == null || encryptedTuple == null || token_w == null) {
            throw new IllegalArgumentException("address, encryptedTuple or token_w cannot be null");
        }
    }
}
