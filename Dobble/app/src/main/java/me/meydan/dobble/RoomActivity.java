package me.meydan.dobble;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class RoomActivity extends AppCompatActivity {
    private TextView roomCodeTextView;
    private TextView statusTextView;
    private TextView playerOneTextView;
    private TextView playerTwoTextView;
    private Button startGameButton;
    private String roomCode;
    private String currentUid;
    private boolean isOwner = false;

    /**
     * Listener that triggers whenever the server sends a "room_updated" event.
     * It parses the room details and refreshes the names displayed on the screen.
     */
    private final Emitter.Listener roomUpdatedListener = args -> {
        if (args.length == 0 || args[0] == null) {
            return;
        }

        try {
            JSONObject room;

            // Make sure the incoming server argument is a valid JSON object
            if (args[0] instanceof JSONObject) {
                room = (JSONObject) args[0];
            } else {
                room = new JSONObject(args[0].toString());
            }

            // Update the user interface fields on the main UI thread
            runOnUiThread(() -> updateRoomUi(room));

        } catch (Exception error) {
            runOnUiThread(() ->
                    Toast.makeText(
                            RoomActivity.this,
                            "Invalid room update",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    };

    /**
     * Listener that triggers when the server sends a "game_started" event.
     * It automatically navigates both players from this lobby screen to the active game screen.
     */
    private final Emitter.Listener gameStartedListener = args ->
            runOnUiThread(() -> {
                Intent intent = new Intent(
                        RoomActivity.this,
                        GameActivity.class
                );

                startActivity(intent);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        // Retrieve passed parameters from the previous screen activity
        roomCode = getIntent().getStringExtra("roomCode");
        String roomJson = getIntent().getStringExtra("roomJson");

        // Close the screen immediately if the room code is blank or broken
        if (roomCode == null || roomCode.trim().isEmpty()) {
            finish();
            return;
        }

        // Fetch the currently active user session
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        // Close the screen if the user session is missing
        if (user == null) {
            finish();
            return;
        }

        currentUid = user.getUid();

        // Connect variables to the XML layout components
        roomCodeTextView = findViewById(R.id.roomCodeTextView);
        statusTextView = findViewById(R.id.statusTextView);
        playerOneTextView = findViewById(R.id.playerOneTextView);
        playerTwoTextView = findViewById(R.id.playerTwoTextView);
        startGameButton = findViewById(R.id.startGameButton);
        Button leaveRoomButton = findViewById(R.id.leaveRoomButton);

        // Put the room code onto the screen text element
        roomCodeTextView.setText(roomCode);

        // If initial room data was passed, parse it and update the screen right away
        if (roomJson != null && !roomJson.trim().isEmpty()) {
            try {
                JSONObject initialRoom = new JSONObject(roomJson);
                updateRoomUi(initialRoom);
            } catch (Exception error) {
                Toast.makeText(
                        RoomActivity.this,
                        "Could not load room details",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        // Attach layout actions to execution methods
        startGameButton.setOnClickListener(v -> startGame());
        leaveRoomButton.setOnClickListener(v -> leaveRoom());

        // Register server listeners for real-time traffic updates
        registerSocketListeners();
    }

    /**
     * Finds the open WebSocket connection and registers listeners for room updates and game starts.
     */
    private void registerSocketListeners() {
        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null) {
            finish();
            return;
        }

        // Turn on incoming event tracking triggers
        socket.on("room_updated", roomUpdatedListener);
        socket.on("game_started", gameStartedListener);
    }

    /**
     * Updates text fields, player names, and buttons depending on who is currently in the room.
     */
    private void updateRoomUi(JSONObject room) {
        // Check if the current user is the creator/owner of the lobby
        String ownerUid = room.optString("ownerUid");
        isOwner = currentUid.equals(ownerUid);

        JSONArray players = room.optJSONArray("players");

        if (players == null) {
            return;
        }

        // Parse first player data if they exist
        if (players.length() >= 1) {
            JSONObject first = players.optJSONObject(0);

            if (first != null) {
                playerOneTextView.setText(
                        first.optString("displayName", "Player 1")
                );
            }
        }

        // Parse second player data if they exist
        if (players.length() >= 2) {
            JSONObject second = players.optJSONObject(1);

            if (second != null) {
                playerTwoTextView.setText(
                        second.optString("displayName", "Player 2")
                );
            }

            // Update label state when room is full
            statusTextView.setText("Both players are ready");

            // Only show the start game button to the room host
            startGameButton.setVisibility(
                    isOwner ? View.VISIBLE : View.GONE
            );

        } else {
            // Keep waiting if the second slot is unoccupied
            playerTwoTextView.setText("Waiting...");
            statusTextView.setText("Waiting for another player...");
            startGameButton.setVisibility(View.GONE);
        }
    }

    /**
     * Tells the server to start the match with a specific point cap target.
     */
    private void startGame() {
        // Safety lock: non-owners cannot trigger match generation
        if (!isOwner) {
            return;
        }

        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            showToast("Not connected to server");
            return;
        }

        JSONObject data = new JSONObject();

        try {
            // Set the target goal score to win the match
            data.put("targetScore", 20);
        } catch (Exception ignored) {
        }

        // Emit the start match event and handle errors if they return
        socket.emit("start_game", data, (Ack) args -> {
            if (args.length == 0) {
                return;
            }

            try {
                JSONObject response;

                if (args[0] instanceof JSONObject) {
                    response = (JSONObject) args[0];
                } else {
                    response = new JSONObject(args[0].toString());
                }

                // If server returns success = false, print out the failure reason
                if (!response.optBoolean("ok", false)) {
                    String error = response.optString(
                            "error",
                            "Could not start game"
                    );

                    runOnUiThread(() -> showToast(error));
                }

            } catch (Exception error) {
                runOnUiThread(() ->
                        showToast("Invalid server response")
                );
            }
        });
    }

    /**
     * Sends a request to the server to disconnect from the room, then goes back to the main dashboard.
     */
    private void leaveRoom() {
        Socket socket = SocketManager.getInstance().getSocket();

        // If socket is down, skip network emission and fallback directly to dashboard
        if (socket == null || !socket.connected()) {
            openMain();
            return;
        }

        // Fire the leave room transaction event, then head home on confirmation response
        socket.emit("leave_room", new JSONObject(), (Ack) args ->
                runOnUiThread(this::openMain)
        );
    }

    /**
     * Helper method to return to the MainActivity dashboard and reset page tracking stacks.
     */
    private void openMain() {
        Intent intent = new Intent(
                RoomActivity.this,
                MainActivity.class
        );

        // Bring the existing MainActivity instance to the front instead of building a new one
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish(); // Terminate room layout completely
    }

    /**
     * Helper method to present message bubbles on screen.
     */
    private void showToast(String message) {
        Toast.makeText(RoomActivity.this,message,Toast.LENGTH_LONG).show();
    }

    /**
     * Lifecycle onDestroy block. Cleans up outstanding WebSocket listener threads
     * to avoid performance lag and data leaks when the page closes.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Socket socket = SocketManager.getInstance().getSocket();

        // Detach live listener bindings to prevent phantom updates
        if (socket != null) {
            socket.off("room_updated", roomUpdatedListener);
            socket.off("game_started", gameStartedListener);
        }
    }
}