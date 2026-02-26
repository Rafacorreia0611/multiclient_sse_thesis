package pt.ul.fc58256.sse.model;

import java.io.Serializable;

public record UpdateTuple(String docId, UpdateOp op) implements Serializable {

    public UpdateTuple {
        if (docId == null || op == null) {
            throw new IllegalArgumentException("docId or op cannot be null");
        }
    }
}
