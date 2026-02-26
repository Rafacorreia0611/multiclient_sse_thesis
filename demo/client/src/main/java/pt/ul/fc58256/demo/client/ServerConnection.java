package pt.ul.fc58256.demo.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import pt.ul.fc58256.sse.model.UpdateTuple;

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

    public List<UpdateTuple> readUpdateTupleList() {
        Object o = read();
        if (o == null) {
            return null;
        }
        if (!(o instanceof List<?> raw)) {
            throw new RuntimeException("Expected List got " + o.getClass().getSimpleName());
        }

        List<UpdateTuple> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof UpdateTuple t)) {
                throw new RuntimeException("Expected UpdateTuple element");
            }
            out.add(t);
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
