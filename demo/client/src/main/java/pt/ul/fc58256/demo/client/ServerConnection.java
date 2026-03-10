package pt.ul.fc58256.demo.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import pt.ul.fc58256.sse.model.EncryptedUpdateTuple;

public class ServerConnection implements AutoCloseable {

    private String host;
    private int port;

    private Socket serverSocket;
    private ObjectInputStream serverIn;
    private ObjectOutputStream serverOut;

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() {
        try {
            serverSocket = new Socket(host, port);
            serverOut = new ObjectOutputStream(serverSocket.getOutputStream());
            serverOut.flush();
            serverIn = new ObjectInputStream(serverSocket.getInputStream());

            System.out.println("Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            throw new RuntimeException("Error connecting to server at " + host + ":" + port, e);
        }
    }

    public void send(Object msg) {
        try {
            serverOut.writeObject(msg);
            serverOut.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error sending message to server", e);
        }
    }

    public Object read() {
        try {
            return serverIn.readObject();
        } catch (IOException e) {
            throw new RuntimeException("Error reading message from server", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing message from server", e);
        }
    }

    public <T> T readExpected(Class<T> cls) {
        if (cls == null) {
            throw new IllegalArgumentException("Expected class cannot be null");
        }
        Object o = read();
        if (!cls.isInstance(o)) {
            throw new RuntimeException("Expected " + cls.getSimpleName() + " got " + o.getClass().getSimpleName());
        }
        return cls.cast(o);
    }

    public Map<EncryptedUpdateTuple, javax.crypto.SecretKey> readEncryptedUpdateTupleMap() {
        Object o = read();
        if (o == null) {
            return null;
        }
        if (!(o instanceof java.util.Map<?, ?> raw)) {
            throw new RuntimeException("Expected Map got " + o.getClass().getSimpleName());
        }

        Map<EncryptedUpdateTuple, javax.crypto.SecretKey> out =
                new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof EncryptedUpdateTuple encryptedTuple)) {
                throw new RuntimeException("Expected EncryptedUpdateTuple key");
            }
            if (!(entry.getValue() instanceof SecretKey secretKey)) {
                throw new RuntimeException("Expected SecretKey value");
            }
            out.put(encryptedTuple, secretKey);
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server connection: " + e.getMessage());
        }
    }
}
