package me.meydan.dobble;

import android.util.Log;

import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * This class manages the network connection to the game server.
 * It uses the Singleton pattern so that the same connection is shared across all screens.
 * It handles connecting, disconnecting, and tracking connection status events.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";

    /*
     * Android Emulator local address: http://10.0.2.2:5000
     * For a real phone, replace this with your computer's local IP address (e.g., http://192.168.1.25:5000).
     */
    private static final String SERVER_URL =
            "http://10.0.2.2:5000";

    // The single instance of this manager class
    private static SocketManager instance;

    // The core Socket.io connection object
    private Socket socket;

    // Private constructor prevents other classes from creating instances directly
    private SocketManager() {
    }

    /**
     * Gets the single shared instance of the SocketManager.
     */
    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }

        return instance;
    }

    /**
     * Closes any old connection and opens a new secure connection using a Firebase token.
     *
     * @param firebaseToken The secure login token from Firebase Auth.
     * @param listener The callback interface to report connection status changes.
     */
    public void connect(
            String firebaseToken,
            ConnectionListener listener
    ) {
        // Clear any leftover connection first
        disconnect();

        try {
            // Package the authentication token to pass to the server
            Map<String, String> auth = new HashMap<>();
            auth.put("token", firebaseToken);

            // Configure socket settings like retry delays and network timeouts
            IO.Options options = IO.Options.builder()
                    .setAuth(auth)
                    .setReconnection(true)          // Retry if connection drops
                    .setReconnectionAttempts(10)    // Try connecting up to 10 times
                    .setReconnectionDelay(1000)     // Wait 1 second between retries
                    .setTimeout(10000)              // Give up after a 10-second timeout
                    .setForceNew(true)              // Create a completely clean socket session
                    .build();

            // Create the socket instance pointing to our server address
            socket = IO.socket(
                    URI.create(SERVER_URL),
                    options
            );

            // Hook up listeners for default connection events
            registerBaseListeners(listener);

            // Start the non-blocking asynchronous connection request
            socket.connect();

        } catch (Exception error) {
            Log.e(TAG, "Failed to create socket", error);

            if (listener != null) {
                listener.onError(error.getMessage());
            }
        }
    }

    /**
     * Binds internal event listeners to track core server status updates.
     */
    private void registerBaseListeners(
            ConnectionListener listener
    ) {
        // Triggered when the physical network socket links up with the server
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected: " + socket.id());

            if (listener != null) {
                listener.onConnected();
            }
        });

        // Triggered when the server verifies the Firebase token and accepts the user
        socket.on("connected", args -> {
            // Safety check: terminate if the server data is missing or empty
            if (args.length == 0 || args[0] == null) {
                Log.e(TAG, "Server sent empty connected event");

                if (listener != null) {
                    listener.onError("Server sent empty connection data");
                }

                return;
            }

            try {
                JSONObject data;

                // Ensure the response parameters conform to a valid JSON data structure
                if (args[0] instanceof JSONObject) {
                    data = (JSONObject) args[0];
                } else {
                    data = new JSONObject(args[0].toString());
                }

                Log.d(TAG, "Server accepted connection: " + data);

                if (listener != null) {
                    listener.onServerAccepted(data);
                }

            } catch (Exception error) {
                Log.e(TAG, "Failed to parse connected event", error);

                if (listener != null) {
                    listener.onError(
                            "Invalid server response: " + error.getMessage()
                    );
                }
            }
        });

        // Triggered if the connection fails entirely (e.g., server offline or wrong address)
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String message = "Connection error";

            if (args.length > 0 && args[0] != null) {
                message = args[0].toString();
            }

            Log.e(TAG, message);

            if (listener != null) {
                listener.onError(message);
            }
        });

        // Triggered when the device disconnects from the server (manually or via network loss)
        socket.on(Socket.EVENT_DISCONNECT, args -> {
            String reason = "Disconnected";

            if (args.length > 0 && args[0] != null) {
                reason = args[0].toString();
            }

            Log.d(TAG, reason);

            if (listener != null) {
                listener.onDisconnected(reason);
            }
        });
    }

    /**
     * Returns the active raw socket instance for custom page-level emissions.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Checks if the network connection is currently live.
     */
    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    /**
     * Completely shuts down the current connection, clears listeners, and wipes memory references.
     */
    public void disconnect() {
        if (socket == null) {
            return;
        }

        socket.off();          // Unbind all custom event handlers to avoid memory leaks
        socket.disconnect();   // Disconnect the socket session from the server
        socket.close();        // Free all background thread channel resources
        socket = null;         // Wipe object reference
    }

    /**
     * Interface used by activities to respond to socket connection status updates.
     */
    public interface ConnectionListener {

        // Runs when the device establishes an initial connection link
        void onConnected();

        // Runs when the server accepts the token and logs the profile into the session
        void onServerAccepted(JSONObject userData);

        // Runs when a network or handshake failure occurs
        void onError(String message);

        // Runs when the connection drops or gets disconnected
        void onDisconnected(String reason);
    }
}
