package me.meydan.dobble;

import android.util.Log;

import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketManager {

    private static final String TAG = "SocketManager";

    /*
     * אמולטור Android:
     * http://10.0.2.2:5000
     *
     * טלפון אמיתי:
     * החלף בכתובת ה-IPv4 של המחשב, לדוגמה:
     * http://192.168.1.25:5000
     */
    private static final String SERVER_URL =
            "http://10.0.2.2:5000";

    private static SocketManager instance;

    private Socket socket;

    private SocketManager() {
    }

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }

        return instance;
    }

    public void connect(
            String firebaseToken,
            ConnectionListener listener
    ) {
        disconnect();

        try {
            Map<String, String> auth = new HashMap<>();
            auth.put("token", firebaseToken);

            IO.Options options = IO.Options.builder()
                    .setAuth(auth)
                    .setReconnection(true)
                    .setReconnectionAttempts(10)
                    .setReconnectionDelay(1000)
                    .setTimeout(10000)
                    .setForceNew(true)
                    .build();

            socket = IO.socket(
                    URI.create(SERVER_URL),
                    options
            );

            registerBaseListeners(listener);

            socket.connect();

        } catch (Exception error) {
            Log.e(TAG, "Failed to create socket", error);

            if (listener != null) {
                listener.onError(error.getMessage());
            }
        }
    }

    private void registerBaseListeners(
            ConnectionListener listener
    ) {
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected: " + socket.id());

            if (listener != null) {
                listener.onConnected();
            }
        });

        socket.on("connected", args -> {
            if (args.length == 0 || args[0] == null) {
                Log.e(TAG, "Server sent empty connected event");

                if (listener != null) {
                    listener.onError("Server sent empty connection data");
                }

                return;
            }

            try {
                JSONObject data;

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

    public Socket getSocket() {
        return socket;
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void disconnect() {
        if (socket == null) {
            return;
        }

        socket.off();
        socket.disconnect();
        socket.close();
        socket = null;
    }

    public interface ConnectionListener {

        void onConnected();

        void onServerAccepted(JSONObject userData);

        void onError(String message);

        void onDisconnected(String reason);
    }
}