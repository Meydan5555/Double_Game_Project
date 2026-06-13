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

    private final Emitter.Listener roomUpdatedListener = args -> {
        if (args.length == 0 || args[0] == null) {
            return;
        }

        try {
            JSONObject room;

            if (args[0] instanceof JSONObject) {
                room = (JSONObject) args[0];
            } else {
                room = new JSONObject(args[0].toString());
            }

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

        roomCode = getIntent().getStringExtra("roomCode");
        String roomJson = getIntent().getStringExtra("roomJson");

        if (roomCode == null || roomCode.trim().isEmpty()) {
            finish();
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        currentUid = user.getUid();

        roomCodeTextView =
                findViewById(R.id.roomCodeTextView);
        statusTextView =
                findViewById(R.id.statusTextView);
        playerOneTextView =
                findViewById(R.id.playerOneTextView);
        playerTwoTextView =
                findViewById(R.id.playerTwoTextView);
        startGameButton =
                findViewById(R.id.startGameButton);

        Button leaveRoomButton =
                findViewById(R.id.leaveRoomButton);

        roomCodeTextView.setText(roomCode);
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

        startGameButton.setOnClickListener(v ->
                startGame()
        );

        leaveRoomButton.setOnClickListener(v ->
                leaveRoom()
        );

        registerSocketListeners();
    }

    private void registerSocketListeners() {
        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null) {
            finish();
            return;
        }

        socket.on("room_updated", roomUpdatedListener);
        socket.on("game_started", gameStartedListener);
    }

    private void updateRoomUi(JSONObject room) {
        String ownerUid = room.optString("ownerUid");
        isOwner = currentUid.equals(ownerUid);

        JSONArray players = room.optJSONArray("players");

        if (players == null) {
            return;
        }

        if (players.length() >= 1) {
            JSONObject first = players.optJSONObject(0);

            if (first != null) {
                playerOneTextView.setText(
                        first.optString("displayName", "Player 1")
                );
            }
        }

        if (players.length() >= 2) {
            JSONObject second = players.optJSONObject(1);

            if (second != null) {
                playerTwoTextView.setText(
                        second.optString("displayName", "Player 2")
                );
            }

            statusTextView.setText("Both players are ready");

            startGameButton.setVisibility(
                    isOwner ? View.VISIBLE : View.GONE
            );

        } else {
            playerTwoTextView.setText("Waiting...");
            statusTextView.setText(
                    "Waiting for another player..."
            );
            startGameButton.setVisibility(View.GONE);
        }
    }

    private void startGame() {
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
            data.put("targetScore", 2);
        } catch (Exception ignored) {
        }

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

    private void leaveRoom() {
        Socket socket = SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            openMain();
            return;
        }

        socket.emit("leave_room", new JSONObject(), (Ack) args ->
                runOnUiThread(this::openMain)
        );
    }

    private void openMain() {
        Intent intent = new Intent(
                RoomActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        Toast.makeText(
                RoomActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Socket socket = SocketManager.getInstance().getSocket();

        if (socket != null) {
            socket.off("room_updated", roomUpdatedListener);
            socket.off("game_started", gameStartedListener);
        }
    }
}