package pt.ul.fc58256.demo.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import pt.ul.fc58256.sse.SseServer;

public class Server {
    public static void main(String[] args) {
        
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be an integer");
            }
        }

        SseServer sseServer = new SseServer();
        
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                // Handle the client connection in a separate thread
                try {
                    Thread clientThread = new Thread(new ClientHandler(clientSocket, sseServer));
                    clientThread.start();
                } catch (Exception e) {
                    System.out.println("Error with client connection: \n" + e.getMessage());
                    try {
                        clientSocket.close();
                    } catch (IOException ioException) {
                        System.out.println("Error closing client socket: " + ioException.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error starting server on port " + port, e);
        }

    }
}
