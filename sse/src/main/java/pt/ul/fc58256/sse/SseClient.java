package pt.ul.fc58256.sse;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import pt.ul.fc58256.sse.crypto.Prf;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateOp;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

public class SseClient {
    
    private SseClient() {
        // Private constructor to prevent instantiation
    }

    public static SearchToken generateSearchToken(SecretKey tokenGenKey, State state, String keyword) {

        byte[] token_w_bytes = Prf.prf(tokenGenKey, keyword);
        String token_w = Base64.getEncoder().encodeToString(token_w_bytes);

        int searchCount = state.searchCounter().getOrDefault(token_w, 0);

        byte[] token_ws_bytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);
        String token_ws = Base64.getEncoder().encodeToString(token_ws_bytes);

        return new SearchToken(token_ws, token_w);
    }

    public static UpdateToken generateUpdateToken(SecretKey tokenGenKey, State state, String keyword, String docId, UpdateOp op, int retryOffset) {

        byte[] token_w_bytes = Prf.prf(tokenGenKey, keyword);
        String token_w = Base64.getEncoder().encodeToString(token_w_bytes);

        int searchCount = state.searchCounter().getOrDefault(token_w, 0);
        int updateCount = state.updateCounter().getOrDefault(token_w, 0);

        byte[] token_ws_bytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

        String address = Base64.getEncoder().encodeToString(Prf.prf(token_ws_bytes, Integer.toString(updateCount + 1 + retryOffset)));
        return new UpdateToken(address, new UpdateTuple(docId, op), token_w);
    }

    public static List<String> extractAddedDocIds(List<UpdateTuple> updates) {
        if (updates == null) {
            throw new IllegalArgumentException("updates cannot be null");
        }

        Set<String> activeDocIds = new LinkedHashSet<>();
        for (UpdateTuple update : updates) {
            if (update == null) {
                continue;
            }
            switch (update.op()) {
                case ADD -> activeDocIds.add(update.docId());
                case DEL -> activeDocIds.remove(update.docId());
            }
        }
        return new ArrayList<>(activeDocIds);
    }
}
