package pt.ul.fc58256.demo.client;

import org.apache.commons.validator.routines.InetAddressValidator;

public class Validators {

    private Validators() {
        // Prevent instantiation
    }

    public static String validateHost(String host) {
        InetAddressValidator validator = InetAddressValidator.getInstance();
        if (!validator.isValid(host)) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        return host;
    }

    public static int validatePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + portStr, e);
        }
    }
}
