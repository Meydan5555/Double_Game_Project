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

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        userTextView = findViewById(R.id.userTextView);
        serverStatusTextView = findViewById(R.id.serverStatusTextView);
        serverProgressBar = findViewById(R.id.serverProgressBar);
        roomCodeEditText = findViewById(R.id.roomCodeEditText);

        createRoomButton = findViewById(R.id.createRoomButton);
        joinRoomButton = findViewById(R.id.joinRoomButton);
        reconnectButton = findViewById(R.id.reconnectButton);
        Button logoutButton = findViewById(R.id.logoutButton);
        Button matchHistoryButton =
                findViewById(R.id.matchHistoryButton);

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            openLogin();
            return;
        }

        displayName = null;
        userTextView.setText("Loading profile...");

        setRoomButtonsEnabled(false);

        loadDisplayName(user);

        createRoomButton.setOnClickListener(v -> createRoom());
        joinRoomButton.setOnClickListener(v -> joinRoom());
        reconnectButton.setOnClickListener(v -> connectToServer());
        logoutButton.setOnClickListener(v -> logout());
        matchHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MatchHistoryActivity.class
            );

            startActivity(intent);
        });

        setRoomButtonsEnabled(false);
        connectToServer();
    }

    private void loadDisplayName(FirebaseUser user) {
        firestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    String savedName =
                            document.getString("displayName");

                    if (savedName != null
                            && !savedName.trim().isEmpty()) {

                        displayName = savedName.trim();

                    } else if (user.getDisplayName() != null
                            && !user.getDisplayName().trim().isEmpty()) {

                        displayName =
                                user.getDisplayName().trim();

                    } else {
                        displayName = user.getEmail();
                    }

                    userTextView.setText(
                            "Hello, " + displayName
                    );

                    if (SocketManager.getInstance().isConnected()) {
                        setRoomButtonsEnabled(true);
                    }
                })
                .addOnFailureListener(error -> {
                    if (user.getDisplayName() != null
                            && !user.getDisplayName().trim().isEmpty()) {

                        displayName =
                                user.getDisplayName().trim();

                    } else {
                        displayName = user.getEmail();
                    }

                    userTextView.setText(
                            "Hello, " + displayName
                    );

                    if (SocketManager.getInstance().isConnected()) {
                        setRoomButtonsEnabled(true);
                    }
                });
    }

    private void connectToServer() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            openLogin();
            return;
        }

        setConnectionLoading(true);
        setRoomButtonsEnabled(false);

        serverStatusTextView.setText("Getting Firebase token...");

        user.getIdToken(true)
                .addOnSuccessListener(result -> {
                    String token = result.getToken();

                    if (token == null || token.trim().isEmpty()) {
                        setConnectionLoading(false);
                        serverStatusTextView.setText("Firebase token is empty");
                        return;
                    }

                    serverStatusTextView.setText("Connecting to server...");

                    SocketManager.getInstance().connect(
                            token,
                            new SocketManager.ConnectionListener() {
                                @Override
                                public void onConnected() {
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        if (displayName != null) {
                                            setRoomButtonsEnabled(true);
                                        }
                                        serverStatusTextView.setText(
                                                "Connected to server"
                                        );
                                    });
                                }

                                @Override
                                public void onServerAccepted(
                                        JSONObject userData
                                ) {
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        if (displayName != null) {
                                            setRoomButtonsEnabled(true);
                                        }
                                        serverStatusTextView.setText(
                                                "Connected to server"
                                        );
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        setRoomButtonsEnabled(false);

                                        serverStatusTextView.setText(
                                                "Connection failed:\n" + message
                                        );
                                    });
                                }

                                @Override
                                public void onDisconnected(String reason) {
                                    runOnUiThread(() -> {
                                        setConnectionLoading(false);
                                        setRoomButtonsEnabled(false);

                                        serverStatusTextView.setText(
                                                "Disconnected:\n" + reason
                                        );
                                    });
                                }
                            }
                    );
                })
                .addOnFailureListener(error -> {
                    setConnectionLoading(false);
                    setRoomButtonsEnabled(false);

                    serverStatusTextView.setText(
                            "Failed to get Firebase token:\n"
                                    + error.getMessage()
                    );
                });
    }

    private void createRoom() {
        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            Toast.makeText(
                    this,
                    "Not connected to server",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        setRoomButtonsEnabled(false);

        JSONObject data = new JSONObject();

        try {
            data.put("displayName", displayName);
        } catch (JSONException error) {
            setRoomButtonsEnabled(true);
            return;
        }

        socket.emit("create_room", data, (Ack) args -> {
            if (args.length == 0) {
                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast("Empty server response");
                });
                return;
            }

            handleRoomResponse(args[0]);
        });
    }

    private void joinRoom() {
        String roomCode =
                roomCodeEditText.getText().toString().trim();

        if (TextUtils.isEmpty(roomCode)) {
            roomCodeEditText.setError("Enter room code");
            return;
        }

        if (roomCode.length() != 6) {
            roomCodeEditText.setError("Room code must contain 6 digits");
            return;
        }

        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            showToast("Not connected to server");
            return;
        }

        setRoomButtonsEnabled(false);

        JSONObject data = new JSONObject();

        try {
            data.put("roomCode", roomCode);
            data.put("displayName", displayName);
        } catch (JSONException error) {
            setRoomButtonsEnabled(true);
            return;
        }

        socket.emit("join_room", data, (Ack) args -> {
            if (args.length == 0) {
                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast("Empty server response");
                });
                return;
            }

            handleRoomResponse(args[0]);
        });
    }

    private void handleRoomResponse(Object responseObject) {
        try {
            JSONObject response;

            if (responseObject instanceof JSONObject) {
                response = (JSONObject) responseObject;
            } else {
                response = new JSONObject(responseObject.toString());
            }

            boolean ok = response.optBoolean("ok", false);

            if (!ok) {
                String error = response.optString(
                        "error",
                        "Room operation failed"
                );

                runOnUiThread(() -> {
                    setRoomButtonsEnabled(true);
                    showToast(error);
                });

                return;
            }

            JSONObject room = response.getJSONObject("room");
            String roomCode = room.getString("code");
            String roomJson = room.toString();

            runOnUiThread(() -> openRoom(roomCode, roomJson));

        } catch (Exception error) {
            runOnUiThread(() -> {
                setRoomButtonsEnabled(true);
                showToast("Invalid server response");
            });
        }
    }

    private void openRoom(String roomCode, String roomJson) {
        Intent intent = new Intent(
                MainActivity.this,
                RoomActivity.class
        );

        intent.putExtra("roomCode", roomCode);
        intent.putExtra("roomJson", roomJson);

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