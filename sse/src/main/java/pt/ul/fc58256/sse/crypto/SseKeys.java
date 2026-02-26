package pt.ul.fc58256.sse.crypto;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class SseKeys {

    private final SecretKey tokenGenKey;

    public SseKeys() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 not available in this JVM/provider", e);
        }
        keyGen.init(256);
        this.tokenGenKey = keyGen.generateKey();
    }

    public SecretKey getTokenGenKey() {
        return tokenGenKey;
    }
}
