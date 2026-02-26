package pt.ul.fc58256.demo.client;

public class Client {
    public static void main(String[] args) {
        
        String host = "127.0.0.1";
        int port = 8080;
        
        if (args.length > 0) {
            host = Validators.validateHost(args[0]);
        }
        if (args.length > 1) {
            port = Validators.validatePort(args[1]);
        }

        try (ServerConnection conn = new ServerConnection(host, port)) {
            conn.connect();
            new ClientCli(conn).run();
        }
    }
}
