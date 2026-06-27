package me.meydan.dobble;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Ack;
import io.socket.client.Socket;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private TextView userTextView;
    private TextView serverStatusTextView;
    private ProgressBar serverProgressBar;
    private EditText roomCodeEditText;

    private Button createRoomButton;
    private Button joinRoomButton;
    private Button reconnectButton;

    private String displayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Authentication and Firestore database instances
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Bind layout views and text containers from the XML layout
        userTextView = findViewById(R.id.userTextView);
        serverStatusTextView = findViewById(R.id.serverStatusTextView);
        serverProgressBar = findViewById(R.id.serverProgressBar);
        roomCodeEditText = findViewById(R.id.roomCodeEditText);

        // Bind dashboard button elements
        createRoomButton = findViewById(R.id.createRoomButton);
        joinRoomButton = findViewById(R.id.joinRoomButton);
        reconnectButton = findViewById(R.id.reconnectButton);
        Button logoutButton = findViewById(R.id.logoutButton);
        Button matchHistoryButton = findViewById(R.id.matchHistoryButton);

        // Fetch the currently authenticated active session
        FirebaseUser user = firebaseAuth.getCurrentUser();

        // Redirect unauthenticated guests back to the registration/login screen
        if (user == null) {
            openLogin();
            return;
        }

        // Initialize state while fetching account metadata asynchronously
        displayName = null;
        userTextView.setText("Loading profile...");

        // Keep room controls disabled until data and connections are established
        setRoomButtonsEnabled(false);

        // Fetch user information from the database
        loadDisplayName(user);

        // Attach dedicated click listener logic to dashboard controls
        createRoomButton.setOnClickListener(v -> createRoom());
        joinRoomButton.setOnClickListener(v -> joinRoom());
        reconnectButton.setOnClickListener(v -> connectToServer());
        logoutButton.setOnClickListener(v -> logout());

        // Open match history panel when requested
        matchHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MatchHistoryActivity.class
            );
            startActivity(intent);
        });

        // Safeguard state and attempt real-time communication establishment
        setRoomButtonsEnabled(false);
        connectToServer();
    }

    /**
     * Fetches the user's display name from the Firestore database asynchronously.
     * If the Firestore document is missing or invalid, it falls back to the Firebase
     * Account profile name, and ultimately to the user's email address if nothing else is available.
     *
     * @param user The current authenticated FirebaseUser object containing account credentials.
     */
    private void loadDisplayName(FirebaseUser user) {
        // Query the "users" collection for the document matching the user's unique ID (UID)
        firestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    // Extract the saved display name field from the Firestore document
                    String savedName = document.getString("displayName");

                    // Priority 1: Use the display name stored in the Firestore database
                    if (savedName != null && !savedName.trim().isEmpty()) {
                        displayName = savedName.trim();
                    }
                    // Priority 2: Fallback to the display name provided by the Firebase Auth profile
                    else if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                        displayName = user.getDisplayName().trim();
                    }
                    // Priority 3: Ultimate fallback to the user's email address
                    else {
                        displayName = user.getEmail();
                    }

                    // Update the UI greeting with the resolved name
                    userTextView.setText("Hello, " + displayName);

                    // Enable room interactions only if a real-time socket connection is already live
                    if (SocketManager.getInstance().isConnected()) {
                        setRoomButtonsEnabled(true);
                    }
                })
                .addOnFailureListener(error -> {
                    // Network/Database fetch failed: Fallback to Firebase profile metadata
                    if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                        displayName = user.getDisplayName().trim();
                    }
                    // Fallback to the user's email if no profile name exists
                    else {
                        displayName = user.getEmail();
                    }

                    // Apply the fallback name to the UI element
                    userTextView.setText("Hello, " + displayName);

                    // Safely authorize room panel buttons if the websocket connection is online
                    if (SocketManager.getInstance().isConnected()) {
                        setRoomButtonsEnabled(true);
                    }
                });
    }

    /**
     * Authenticates and establishes a secure websocket connection with the game server.
     * It requests a fresh Firebase ID token asynchronously, passes it to the SocketManager,
     * and listens for connection lifecycle callbacks to update the user interface accordingly.
     */
    private void connectToServer() {
        // Fetch the currently active Firebase user session
        FirebaseUser user = firebaseAuth.getCurrentUser();

        // Redirect to the login screen if the user is unauthenticated
        if (user == null) {
            openLogin();
            return;
        }

        // Toggle UI states to display loading animations and block room interaction flags
        setConnectionLoading(true);
        setRoomButtonsEnabled(false);

        serverStatusTextView.setText("Getting Firebase token...");

        // Fetch a fresh, forced-refreshed Firebase Authentication JSON Web Token (JWT)
        user.getIdToken(true)
                .addOnSuccessListener(result -> {
                    String token = result.getToken();

                    // Handle edge cases where the retrieved authentication token is blank or missing
                    if (token == null || token.trim().isEmpty()) {
                        setConnectionLoading(false);
                        serverStatusTextView.setText("Firebase token is empty");
                        return;
                    }

                    serverStatusTextView.setText("Connecting to server...");

                    // Initialize the websocket connection via SocketManager using the verified token
                    SocketManager.getInstance().connect(
                            token,
                            new SocketManager.ConnectionListener() {
                                @Override
                                public void onConnected() {
                                    // Handle underlying transport-level connection success on the UI thread
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        // Allow room interaction only if the profile display name is already loaded
                                        if (displayName != null) {
                                            setRoomButtonsEnabled(true);
                                        }
                                        serverStatusTextView.setText("Connected to server");
                                    });
                                }

                                @Override
                                public void onServerAccepted(JSONObject userData) {
                                    // Handle application-level authentication handshake validation success
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        if (displayName != null) {
                                            setRoomButtonsEnabled(true);
                                        }
                                        serverStatusTextView.setText("Connected to server");
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    // Handle connection errors or handshake failures
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        setRoomButtonsEnabled(false);
                                        serverStatusTextView.setText("Connection failed:\n" + message);
                                    });
                                }

                                @Override
                                public void onDisconnected(String reason) {
                                    // Handle sudden connection terminations or manual clean closures
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        setRoomButtonsEnabled(false);
                                        serverStatusTextView.setText("Disconnected:\n" + reason);
                                    });
                                }
                            }
                    );
                })
                .addOnFailureListener(error -> {
                    // Handle failure to fetch the Firebase ID token from local cash or network auth nodes
                    setConnectionLoading(false);
                    setRoomButtonsEnabled(false);
                    serverStatusTextView.setText("Failed to get Firebase token:\n" + error.getMessage());
                });
    }

    /**
     * Requests the game server to create a new match room.
     * It ensures a stable socket connection is available, compiles the user's
     * metadata into a JSON payload, and emits a "create_room" event with an
     * acknowledgement callback to capture the server's response.
     */
    private void createRoom() {
        // Retrieve the current underlying WebSocket instance
        Socket socket = SocketManager.getInstance().getSocket();

        // Abort the operation if the socket instance is uninitialized or disconnected
        if (socket == null || !socket.connected()) {
            Toast.makeText(
                    this,
                    "Not connected to server",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // Disable interaction elements to prevent multiple rapid submission requests
        setRoomButtonsEnabled(false);

        // Construct the JSON payload containing the creator's identity data
        JSONObject data = new JSONObject();
        try {
            data.put("displayName", displayName);
        } catch (JSONException error) {
            // Re-enable room panel actions if JSON construction fails unexpectedly
            setRoomButtonsEnabled(true);
            return;
        }

        // Emit the "create_room" network transaction event accompanied by an asynchronous response listener
        socket.emit("create_room", data, (Ack) args -> {
            // Validate that the server actually returned response parameters
            if (args.length == 0) {
                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast("Empty server response");
                });
                return;
            }

            // Route the structural data packet to the room response processing method
            handleRoomResponse(args[0]);
        });
    }

    /**
     * Attempts to join an existing game room using a user-provided access code.
     * It validates the input format locally, confirms server connectivity, packs
     * the code and user information into a JSON object, and transmits a "join_room"
     * event via WebSockets.
     */
    private void joinRoom() {
        // Read the entered text and remove any trailing or leading whitespace
        String roomCode = roomCodeEditText.getText().toString().trim();

        // Local Validation: Verify that the input field is not empty
        if (TextUtils.isEmpty(roomCode)) {
            roomCodeEditText.setError("Enter room code");
            return;
        }

        // Local Validation: Enforce exact 6-character length constraint for room codes
        if (roomCode.length() != 6) {
            roomCodeEditText.setError("Room code must contain 6 digits");
            return;
        }

        // Retrieve the current persistent Socket object
        Socket socket = SocketManager.getInstance().getSocket();

        // Abort the network request if the device is currently offline or disconnected
        if (socket == null || !socket.connected()) {
            showToast("Not connected to server");
            return;
        }

        // Disable UI action triggers to avoid concurrent registration attempts
        setRoomButtonsEnabled(false);

        // Bundle input code and user profile data into a single request container
        JSONObject data = new JSONObject();
        try {
            data.put("roomCode", roomCode);
            data.put("displayName", displayName);
        } catch (JSONException error) {
            // Restore UI click listeners if serialization runs into errors
            setRoomButtonsEnabled(true);
            return;
        }

        // Dispatch payload to server with an acknowledgement handler for processing feedback
        socket.emit("join_room", data, (Ack) args -> {
            // Confirm that the server response argument array contains structural data
            if (args.length == 0) {
                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast("Empty server response");
                });
                return;
            }

            // Extract the primary data packet and delegate to response handling workflows
            handleRoomResponse(args[0]);
        });
    }

    /**
     * Parses and processes the server's acknowledgement response following a room operation.
     * It dynamically sanitizes the raw input object into a JSON format, evaluates operational
     * success flags, and either updates the UI with error alerts or routes the application
     * to the active game room session.
     *
     * @param responseObject The raw, polymorphic response payload received from the network socket callback.
     */
    private void handleRoomResponse(Object responseObject) {
        try {
            JSONObject response;

            // Safe Casting: Dynamically ensure structural payload compliance before evaluating properties
            if (responseObject instanceof JSONObject) {
                response = (JSONObject) responseObject;
            } else {
                response = new JSONObject(responseObject.toString());
            }

            // Inspect the primary success state flag, defaulting to false if unspecified
            boolean ok = response.optBoolean("ok", false);

            // Handle server-side failure messages or missing validation blocks
            if (!ok) {
                String error = response.optString(
                        "error",
                        "Room operation failed"
                );

                // Re-enable interactions and alert the user to the failure on the UI thread
                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast(error);
                });

                return;
            }

            // Extract the validated room object structure and its identifier tokens
            JSONObject room = response.getJSONObject("room");
            String roomCode = room.getString("code");
            String roomJson = room.toString();

            // Secure navigation and handover of context data payloads on the UI thread
            runOnUiThread(() -> openRoom(roomCode, roomJson));

        } catch (Exception error) {
            // Intercept parse, serialization, or field access runtime execution errors safely
            runOnUiThread(() -> {
                setRoomButtonsEnabled(true);
                showToast("Invalid server response");
            });
        }
    }

    /**
     * Navigates the user from the main dashboard to the specific game room screen.
     * It packages the unique identifier code and the structural state payload of the room
     * as Intent extras, passing them seamlessly to the incoming activity.
     *
     * @param roomCode The unique 6-digit identification code of the target game room.
     * @param roomJson The serialized JSON string containing metadata and player rosters for the room.
     */
    private void openRoom(String roomCode, String roomJson) {
        // Initialize an explicit intent to launch the RoomActivity screen
        Intent intent = new Intent(
                MainActivity.this,
                RoomActivity.class
        );

        // Attach the room configuration parameters as key-value intent data blocks
        intent.putExtra("roomCode", roomCode);
        intent.putExtra("roomJson", roomJson);

        // Execute the navigation transaction request
        startActivity(intent);
    }


    private void setConnectionLoading(boolean loading) {
        serverProgressBar.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );

        reconnectButton.setEnabled(!loading);
    }

    private void setRoomButtonsEnabled(boolean enabled) {
        createRoomButton.setEnabled(enabled);
        joinRoomButton.setEnabled(enabled);
    }

    private void showToast(String message) {
        Toast.makeText(
                MainActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void logout() {
        SocketManager.getInstance().disconnect();
        firebaseAuth.signOut();
        openLogin();
    }

    private void openLogin() {
        Intent intent = new Intent(
                MainActivity.this,
                LoginActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}