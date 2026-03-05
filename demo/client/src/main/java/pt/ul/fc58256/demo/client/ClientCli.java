package pt.ul.fc58256.demo.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.crypto.SecretKey;

import pt.ul.fc58256.demo.common.RequestType;
import pt.ul.fc58256.demo.common.UpdateStatus;
import pt.ul.fc58256.sse.SseClient;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateOp;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

public class ClientCli {

    private final ServerConnection conn;
    private final BufferedReader userIn;

    public ClientCli(ServerConnection conn) {
        this.conn = conn;
        this.userIn = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
        if (conn == null) {
            throw new IllegalStateException("Server connection is not initialized");
        }
        while (true) {
            System.out.println(printMenu());
            String input = readUserLine();

            if (input == null) {
                System.out.println("Input closed. Exiting client.");
                return;
            }

            switch (input.trim().toLowerCase()) {
                case "1", "search", "s" ->
                    handleSearch();
                case "2", "add", "a" ->
                    handleUpdate("Add");
                case "3", "delete", "d" ->
                    handleUpdate("Delete");
                case "4", "exit", "e", "q", "quit" -> {
                    System.out.println("Exiting client.");
                    return;
                }
                default ->
                    System.out.println("Invalid option: " + input);
            }
        }
    }

    private String printMenu() {
        return """
                Operations:
                1) Search 
                2) Add 
                3) Delete 
                4) Exit""";
    }

    private String readUserLine() {
        try {
            return userIn.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Error reading input from terminal", e);
        }
    }

    private void handleSearch() {
        System.out.println("Search selected.\n");
        System.out.println("Enter the keyword to search for:");
        String keyword = readUserLine();
        if (keyword == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("Keyword: " + keyword);

        conn.send(RequestType.SEARCH);

        State state = conn.readExpected(State.class);
        SecretKey tokenGenKey = conn.readExpected(SecretKey.class);

        SearchToken token = SseClient.generateSearchToken(tokenGenKey, state, keyword);
        conn.send(token);

        List<UpdateTuple> results = conn.readUpdateTupleList();
        List<String> docIds = SseClient.extractAddedDocIds(results);
        System.out.println("Matching docIds: " + docIds);

    }

    private void handleUpdate(String op) {
        boolean isAdd = op.equalsIgnoreCase("Add");

        System.out.println((isAdd ? "Add association" : "Remove association") + " selected.\n");
        System.out.println(isAdd
                ? "Enter the keyword to associate with a document:"
                : "Enter the keyword whose association with a document you want to remove:");
        String keyword = readUserLine();
        if (keyword == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("Keyword: " + keyword);
        System.out.println(isAdd
                ? "Enter the document identifier (docId) to associate with that keyword:"
                : "Enter the document identifier (docId) to disassociate from that keyword:");
        String docId = readUserLine();
        if (docId == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("DocId: " + docId);

        conn.send(RequestType.UPDATE);

        State state = conn.readExpected(State.class);
        SecretKey tokenGenKey = conn.readExpected(SecretKey.class);

        UpdateOp updateOp = isAdd ? UpdateOp.ADD : UpdateOp.DEL;

        sendUpdateToken(tokenGenKey, state, keyword, docId, updateOp);
    }

    private void sendUpdateToken(SecretKey tokenGenKey, State state, String keyword, String docId, UpdateOp op) {
        int retryOffset = 0;
        while (true) {
            UpdateToken updateToken;
            try{
                updateToken = SseClient.generateUpdateToken(tokenGenKey, state, keyword, docId, op, retryOffset);
            } catch (IllegalArgumentException e) {
                System.out.println("Error generating update token: " + e.getMessage());
                return;
            }
            if (updateToken == null) {
                System.out.println("Failed to generate update token. Please try again.");
                return;
            }
            conn.send(updateToken);
            UpdateStatus status = conn.readExpected(UpdateStatus.class);
            if (status == UpdateStatus.OK) {
                System.out.println("Update successful.");
                break;
            } else {
                System.out.println("Update failed. Retrying...");
                retryOffset++;
            }
        }
    }
}
