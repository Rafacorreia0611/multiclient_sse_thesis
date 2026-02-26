package pt.ul.fc58256.demo.server;


import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

import pt.ul.fc58256.demo.common.RequestType;
import pt.ul.fc58256.demo.common.UpdateStatus;
import pt.ul.fc58256.sse.SseServer;
import pt.ul.fc58256.sse.model.SearchToken;
import pt.ul.fc58256.sse.model.State;
import pt.ul.fc58256.sse.model.UpdateToken;
import pt.ul.fc58256.sse.model.UpdateTuple;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final SseServer sseServer;

    private ObjectInputStream clientIn;
    private ObjectOutputStream clientOut;

    public ClientHandler(Socket clientSocket, SseServer sseServer) {
        this.clientSocket = clientSocket;
        this.sseServer = sseServer;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    @Override
    public void run() {
        try {
            connect();

            while (true) {
                RequestType request = readRequest();
                if (request == null) {
                    return;
                }

                switch (request) {
                    case SEARCH -> handleSearch();
                    case UPDATE -> handleUpdate();
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Client handler stopped: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void connect() {
        try {
            clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
            clientOut.flush();
            clientIn = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Error setting up I/O streams for client connection", e);
        }
    }

    private void send(Object msg) {
        try {
            clientOut.writeObject(msg);
            clientOut.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error sending message to client", e);
        }
    }

    private Object read() {
        try {
            return clientIn.readObject();
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Error reading message from client", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing message from client", e);
        }
    }

    private <T> T readExpected(Class<T> cls) {
        if (cls == null) {
            throw new IllegalArgumentException("Expected class cannot be null");
        }
        Object o = read();
        if (o == null) {
            return null;
        }
        if (!cls.isInstance(o)) {
            throw new RuntimeException("Expected " + cls.getSimpleName() + " got " + o.getClass().getSimpleName());
        }
        return cls.cast(o);
    }

    private RequestType readRequest() {
        return readExpected(RequestType.class);
    }

    private void handleSearch() {
        State searchState = sseServer.getStateSearchToken();
        send(searchState);
        send(sseServer.getTokenGenKey());

        SearchToken searchToken = readExpected(SearchToken.class);
        if (searchToken == null) {
            throw new RuntimeException("Client disconnected before sending search token");
        }

        List<UpdateTuple> searchResults = sseServer.searchQuery(searchToken);
        send(searchResults);
    }

    private void handleUpdate() {
        State updateState = sseServer.getStateUpdateToken();
        send(updateState);
        send(sseServer.getTokenGenKey());

        while (true) {
            UpdateToken updateToken = readExpected(UpdateToken.class);
            if (updateToken == null) {
                throw new RuntimeException("Client disconnected before sending update token");
            }
            try {
                sseServer.updateQuery(updateToken);
                send(UpdateStatus.OK);
                break;
            } catch (IllegalArgumentException e) {
                send(UpdateStatus.RETRY);
            }
        }
    }

    private void close() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }
}
