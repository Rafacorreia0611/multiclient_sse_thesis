package pt.ul.fc58256.sse.model;

import java.io.Serializable;
import java.util.Arrays;

import javax.crypto.SealedObject;

public record EncryptedUpdateTuple(SealedObject encryptedTuple, byte[] iv) implements Serializable {
    public EncryptedUpdateTuple {
        if (encryptedTuple == null || iv == null) {
            throw new IllegalArgumentException("encryptedTuple and iv cannot be null");
        }
        iv = Arrays.copyOf(iv, iv.length);
    }

    @Override
    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }
}
