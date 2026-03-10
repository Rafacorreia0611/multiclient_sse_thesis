package pt.ul.fc58256.sse;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import pt.ul.fc58256.sse.crypto.Prf;
import pt.ul.fc58256.sse.crypto.TupleEncryption;
import pt.ul.fc58256.sse.model.EncryptedUpdateTuple;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

public class SseClient {
    
    private SseClient() {
        // Private constructor to prevent instantiation
    }

    public static SecretKey generateTupleSecretKey(){
        return TupleEncryption.generateRandomKey();
    }

    public static byte[] generateTupleIv(){
        return TupleEncryption.generateIv();
    }

    public static EncryptedUpdateTuple encryptUpdateTuple(SecretKey key, byte[] iv, UpdateTuple tuple) throws NoSuchAlgorithmException,
        NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException {
        return new EncryptedUpdateTuple(TupleEncryption.encryptTuple(key, iv, tuple), iv);
    }

    public static UpdateTuple decryptUpdateTuple(SecretKey key, byte[] iv, EncryptedUpdateTuple encryptedTuple) throws NoSuchPaddingException,
        NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, ClassNotFoundException, 
        IllegalBlockSizeException, IOException, BadPaddingException {
        return TupleEncryption.decryptTuple(key, iv, encryptedTuple.encryptedTuple());
    }

    public static SearchToken generateSearchToken(SecretKey tokenGenKey, State state, String keyword) {

        byte[] token_w_bytes = Prf.prf(tokenGenKey, keyword);
        String token_w = Base64.getEncoder().encodeToString(token_w_bytes);

        int searchCount = state.searchCounter().getOrDefault(token_w, 0);

        byte[] token_ws_bytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);
        String token_ws = Base64.getEncoder().encodeToString(token_ws_bytes);

        return new SearchToken(token_ws, token_w, searchCount);
    }

    public static UpdateToken generateUpdateToken(SecretKey tokenGenKey, State state, String keyword, EncryptedUpdateTuple encryptedTuple, int retryOffset) {

        if (keyword == null || keyword.isEmpty() || encryptedTuple == null) {
             throw new IllegalArgumentException("keyword, encryptedTuple cannot be null or empty");
        }

        byte[] token_w_bytes = Prf.prf(tokenGenKey, keyword);
        String token_w = Base64.getEncoder().encodeToString(token_w_bytes);

        int searchCount = state.searchCounter().getOrDefault(token_w, 0);
        int updateCount = state.updateCounter().getOrDefault(token_w, 0);

        byte[] token_ws_bytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

        String address = Base64.getEncoder().encodeToString(Prf.prf(token_ws_bytes, Integer.toString(updateCount + 1 + retryOffset)));
        return new UpdateToken(address, encryptedTuple, token_w);
    }

    public static List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
        if (updates == null) {
            throw new IllegalArgumentException("updates cannot be null");
        }

        Set<String> activeDocIds = new LinkedHashSet<>();
        for (Map.Entry<EncryptedUpdateTuple, SecretKey> entry : updates.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            UpdateTuple update;
            try {
                update = decryptUpdateTuple(entry.getValue(), entry.getKey().iv(), entry.getKey());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt update tuple", e);
            }

            switch (update.op()) {
                case ADD -> activeDocIds.add(update.docId());
                case DEL -> activeDocIds.remove(update.docId());
            }
        }
        return new ArrayList<>(activeDocIds);
    }
}
