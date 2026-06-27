package me.meydan.dobble;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class GameActivity extends AppCompatActivity {

    private ImageView centerCardImageView;
    private ImageView playerCardImageView;

    private TextView roundTextView;
    private TextView scoreTextView;
    private TextView gameStatusTextView;
    private String currentPlayerCardId;

    private CardDataManager cardDataManager;

    private boolean clickEnabled = true;

    /**
     * Listens for game state updates from the server.
     * Parses the incoming JSON object and updates the UI on the main thread.
     */
    // Inline comment inside the try block:
    // Verify data format and trigger UI update safely on the main UI thread
    private final Emitter.Listener gameStateListener = args -> {
        if (args.length == 0 || args[0] == null) {
            return;
        }

        try {
            JSONObject gameState;

            if (args[0] instanceof JSONObject) {
                gameState = (JSONObject) args[0];
            } else {
                gameState = new JSONObject(args[0].toString());
            }

            runOnUiThread(() -> updateGameState(gameState));

        } catch (Exception error) {
            runOnUiThread(() ->
                    Toast.makeText(
                            GameActivity.this,
                            "Invalid game state",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    };

    /**
     * Handles incorrect symbol selections.
     * Temporarily disables clicks and flashes an error message to the player.
     */
    // Inline comment inside postDelayed:
    // Revert the status text and color back to normal after 800 milliseconds
    private final Emitter.Listener wrongSymbolListener = args ->
            runOnUiThread(() -> {
                clickEnabled = true;

                gameStatusTextView.setText(
                        "Wrong symbol!"
                );

                gameStatusTextView.setTextColor(
                        getColor(
                                android.R.color.holo_red_dark
                        )
                );

                gameStatusTextView.postDelayed(
                        () -> {
                            gameStatusTextView.setText(
                                    "Find the matching symbol!"
                            );

                            gameStatusTextView.setTextColor(
                                    getColor(
                                            R.color.dobble_text_dark
                                    )
                            );
                        },
                        800
                );
            });

    /**
     * Listens for the round resolution to display the correct matching symbol.
     */
    private final Emitter.Listener roundResultListener = args -> {
        if (args.length == 0 || args[0] == null) {
            return;
        }

        try {
            JSONObject result;

            if (args[0] instanceof JSONObject) {
                result = (JSONObject) args[0];
            } else {
                result = new JSONObject(args[0].toString());
            }

            String correctSymbol =
                    result.optString("correctSymbol", "");

            runOnUiThread(() -> {
                gameStatusTextView.setText(
                        "Correct symbol: " + correctSymbol
                );

                gameStatusTextView.setTextColor(
                        getColor(android.R.color.holo_green_dark)
                );
            });

        } catch (Exception ignored) {
        }
    };

    /**
     * Listens for the game-over signal to extract final data and end the match.
     */
    private final Emitter.Listener gameOverListener = args -> {
        if (args.length == 0 || args[0] == null) {
            return;
        }

        try {
            JSONObject gameOver;

            if (args[0] instanceof JSONObject) {
                gameOver = (JSONObject) args[0];
            } else {
                gameOver = new JSONObject(
                        args[0].toString()
                );
            }

            runOnUiThread(() ->
                    openGameOver(gameOver)
            );

        } catch (Exception error) {
            runOnUiThread(() ->
                    showToast("Invalid game-over data")
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        centerCardImageView =
                findViewById(R.id.centerCardImageView);

        playerCardImageView =
                findViewById(R.id.playerCardImageView);

        roundTextView =
                findViewById(R.id.roundTextView);

        scoreTextView =
                findViewById(R.id.scoreTextView);

        gameStatusTextView =
                findViewById(R.id.gameStatusTextView);
        cardDataManager =
                CardDataManager.getInstance(this);

        setupCardRegions();

        registerSocketListeners();
    }

    /**
     * Maps all 9 clickable grid layout zones representing the Dobble card regions.
     */
    private void setupCardRegions() {
        setupRegion(
                R.id.regionTopLeft,
                "top-left"
        );

        setupRegion(
                R.id.regionTopCenter,
                "top-center"
        );

        setupRegion(
                R.id.regionTopRight,
                "top-right"
        );

        setupRegion(
                R.id.regionCenterLeft,
                "center-left"
        );

        setupRegion(
                R.id.regionCenterCenter,
                "center-center"
        );

        setupRegion(
                R.id.regionCenterRight,
                "center-right"
        );

        setupRegion(
                R.id.regionBottomLeft,
                "bottom-left"
        );

        setupRegion(
                R.id.regionBottomCenter,
                "bottom-center"
        );

        setupRegion(
                R.id.regionBottomRight,
                "bottom-right"
        );
    }

    /**
     * Configures an individual card region with a click listener.
     * Validates player input before passing the selected symbol to the server.
     *
     * @param viewId The resource ID of the target layout view.
     * @param regionName The string key representing the card sector.
     */
    // Inline comment inside lambda:
    // Block click events if a network request is pending or card is missing
    private void setupRegion(
            int viewId,
            String regionName
    ) {
        View regionView = findViewById(viewId);

        regionView.setOnClickListener(v -> {
            if (!clickEnabled) {
                return;
            }

            if (currentPlayerCardId == null) {
                return;
            }

            String symbol =
                    cardDataManager.getSymbol(
                            currentPlayerCardId,
                            regionName
                    );

            if (symbol == null || symbol.trim().isEmpty()) {
                return;
            }

            flashRegion(regionView);
            sendSymbolClick(symbol);
        });
    }

    private void flashRegion(View regionView) {
        regionView.setBackgroundColor(
                Color.argb(
                        110,
                        255,
                        214,
                        0
                )
        );

        regionView.postDelayed(
                () -> regionView.setBackgroundColor(
                        Color.TRANSPARENT
                ),
                200
        );
    }

    /**
     * Packages the clicked symbol data into a JSON payload and emits it to the server via WebSockets.
     * Temporarily disables further clicks to prevent race conditions and duplicate submissions.
     * Handles the server's acknowledgment response to manage the input lock state.
     *
     * @param symbol The value or identifier of the clicked symbol on the card.
     */
    private void sendSymbolClick(String symbol) {
        // Retrieve the global active socket instance
        Socket socket =
                SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            showToast("Not connected to server");
            return;
        }

        // Lock user interaction to prevent multiple fast clicks while waiting for server response
        clickEnabled = false;

        JSONObject data = new JSONObject();

        try {
            // Construct the JSON payload with the current card and the clicked symbol
            data.put(
                    "cardId",
                    currentPlayerCardId
            );

            data.put(
                    "symbol",
                    symbol
            );

        } catch (Exception error) {
            // Unlock input interaction if the payload construction fails
            clickEnabled = true;
            return;
        }

        // Emit the event to the server and register an Acknowledgment callback
        socket.emit(
                "symbol_click",
                new Object[]{data},
                (Ack) args -> {
                    // Safety check for null or completely empty server responses
                    if (args.length == 0 || args[0] == null) {
                        runOnUiThread(() -> {
                            clickEnabled = true;
                            showToast("Empty server response");
                        });

                        return;
                    }

                    try {
                        JSONObject response;

                        // Safely cast or parse the response argument into a JSONObject
                        if (args[0] instanceof JSONObject) {
                            response =
                                    (JSONObject) args[0];
                        } else {
                            response =
                                    new JSONObject(
                                            args[0].toString()
                                    );
                        }

                        // Extract status flags from the JSON response structure
                        boolean ok =
                                response.optBoolean(
                                        "ok",
                                        false
                                );

                        boolean correct =
                                response.optBoolean(
                                        "correct",
                                        false
                                );

                        // Handle server-side errors or illegal game moves
                        if (!ok) {
                            String error =
                                    response.optString(
                                            "error",
                                            "Click failed"
                                    );

                            runOnUiThread(() -> {
                                clickEnabled = true;
                                showToast(error);
                            });

                            return;
                        }

                        // Re-enable input if the move was processed but the symbol chosen was wrong
                        if (!correct) {
                            runOnUiThread(() ->
                                    clickEnabled = true
                            );
                        }

                    } catch (Exception error) {
                        // Handle malformed JSON structures returned by the socket channel
                        runOnUiThread(() -> {
                            clickEnabled = true;
                            showToast(
                                    "Invalid server response"
                            );
                        });
                    }
                }
        );
    }


    private void showToast(String message) {
        Toast.makeText(
                GameActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    /**
     * Registers active WebSocket event listeners for incoming game updates and errors.
     * After successfully attaching the event handlers, it requests the initial game
     * state from the server and syncs the UI layout accordingly.
     */
    private void registerSocketListeners() {
        // Retrieve the global active socket instance
        Socket socket =
                SocketManager.getInstance().getSocket();

        // Validate socket availability and active network connection state
        if (socket == null || !socket.connected()) {
            Toast.makeText(
                    this,
                    "Not connected to server",
                    Toast.LENGTH_LONG
            ).show();

            // Terminate the activity immediately if no connection exists
            finish();
            return;
        }

        // Attach event listeners to catch incoming asynchronous server events
        socket.on("game_state", gameStateListener);
        socket.on("wrong_symbol", wrongSymbolListener);
        socket.on("round_result", roundResultListener);
        socket.on("game_over", gameOverListener);

        // Emit an initial request to fetch the current game state state upon loading
        socket.emit(
                "get_game_state",
                new Object[]{new JSONObject()},
                (Ack) args -> {
                    // Check for empty or null responses from the server acknowledgment channel
                    if (args.length == 0 || args[0] == null) {
                        return;
                    }

                    try {
                        JSONObject response;

                        // Safely parse or cast the acknowledgment argument into a JSONObject
                        if (args[0] instanceof JSONObject) {
                            response = (JSONObject) args[0];
                        } else {
                            response =
                                    new JSONObject(args[0].toString());
                        }

                        // Exit if the operation status flag indicates a server-side failure
                        if (!response.optBoolean("ok", false)) {
                            return;
                        }

                        // Extract the nested game data configuration block
                        JSONObject game =
                                response.optJSONObject("game");

                        // If game data is valid, update the visual components on the main thread
                        if (game != null) {
                            runOnUiThread(() ->
                                    updateGameState(game)
                            );
                        }

                    } catch (Exception ignored) {
                        // Suppress parsing exceptions to avoid unexpected application crashes
                    }
                }
        );
    }

    /**
     * Extracts tournament statistics, player details, and final scores from the
     * game-over JSON payload, packs them into an Intent, and launches GameOverActivity.
     * Finishes the current activity to prevent the user from navigating back to the active game.
     *
     * @param gameOver The JSON object payload containing final rankings, scores, and match metadata.
     */
    private void openGameOver(JSONObject gameOver) {
        try {
            // Extract the final score mappings and the array of participating players
            JSONObject scores =
                    gameOver.getJSONObject("scores");

            org.json.JSONArray players =
                    gameOver.getJSONArray("players");

            // Retrieve configuration data blocks for both match contestants
            JSONObject firstPlayer =
                    players.getJSONObject(0);

            JSONObject secondPlayer =
                    players.getJSONObject(1);

            // Fetch structural identification UIDs for each user entity
            String firstUid =
                    firstPlayer.getString("uid");

            String secondUid =
                    secondPlayer.getString("uid");

            // Extract display names with safe fallback default values if unassigned
            String firstName =
                    firstPlayer.optString(
                            "displayName",
                            "Player 1"
                    );

            String secondName =
                    secondPlayer.optString(
                            "displayName",
                            "Player 2"
                    );

            // Lookup individual point totals using unique player UIDs as JSON keys
            int firstScore =
                    scores.optInt(firstUid, 0);

            int secondScore =
                    scores.optInt(secondUid, 0);

            // Construct navigation pipeline payload towards the Game-Over layout terminal
            Intent intent = new Intent(
                    GameActivity.this,
                    GameOverActivity.class
            );

            // Map and bundle match result data properties into Intent extras
            intent.putExtra(
                    "winnerUid",
                    gameOver.optString("winnerUid")
            );

            intent.putExtra("firstUid", firstUid);
            intent.putExtra("secondUid", secondUid);

            intent.putExtra("firstName", firstName);
            intent.putExtra("secondName", secondName);

            intent.putExtra("firstScore", firstScore);
            intent.putExtra("secondScore", secondScore);

            // Dynamically resolve whether statistical tracking records were successfully archived
            Object statsSavedValue =
                    gameOver.opt("statsSaved");

            // Support loose structural checking across multiple formats (Boolean, String, or MatchID presence)
            boolean statsSaved =
                    Boolean.TRUE.equals(statsSavedValue)
                            || "true".equalsIgnoreCase(
                            String.valueOf(statsSavedValue)
                    )
                            || gameOver.has("matchId");

            intent.putExtra(
                    "statsSaved",
                    statsSaved
            );

            // Route execution flow directly to the results screen and terminate active gameplay layer
            startActivity(intent);
            finish();

        } catch (Exception error) {
            // Safely notify the player via UI feedback toast channel if data schema extraction fails
            showToast(
                    "Could not open game-over screen"
            );
        }
    }


    /**
     * Synchronizes the user interface elements with the latest game state data.
     * Updates card images, round markers, and scoreboards on the active screen layout.
     * Re-enables touch tracking inputs to unlock player click actions for the new turn.
     *
     * @param state The JSON configuration block containing current turn properties.
     */
    private void updateGameState(JSONObject state) {
        // Extract structural card identification strings from the server state payload
        String centerCardId =
                state.optString("centerCardId");

        String playerCardId =
                state.optString("playerCardId");

        // Cache the newly assigned card reference in memory and unlock screen tap interaction
        currentPlayerCardId = playerCardId;
        clickEnabled = true;

        // Extract and display the active match progression cycle count
        int roundNumber =
                state.optInt("roundNumber", 1);

        roundTextView.setText(
                "Round " + roundNumber
        );

        // Process score mapping object if populated inside the state update package
        JSONObject scores =
                state.optJSONObject("scores");

        if (scores != null) {
            String[] keys = new String[2];
            int index = 0;

            // Iterate over the keys to extract both unique participant identifiers
            java.util.Iterator<String> iterator =
                    scores.keys();

            while (iterator.hasNext() && index < 2) {
                keys[index] = iterator.next();
                index++;
            }

            // Verify both player record entries were successfully captured before rendering
            if (index == 2) {
                int firstScore =
                        scores.optInt(keys[0], 0);

                int secondScore =
                        scores.optInt(keys[1], 0);

                // Update scoreboard text layer layout with the compiled match counts
                scoreTextView.setText(
                        firstScore + " - " + secondScore
                );
            }
        }

        // Render graphical card assets asynchronously onto their respective layout view channels
        showCard(
                centerCardImageView,
                centerCardId
        );

        showCard(
                playerCardImageView,
                playerCardId
        );

        // Reset the contextual help string and restore visual color properties to default state
        gameStatusTextView.setText(
                "Find the matching symbol!"
        );

        gameStatusTextView.setTextColor(
                getColor(R.color.dobble_text_dark)
        );
    }


    /**
     * Dynamically resolves a drawable resource ID based on the provided card identifier
     * and applies the resolved asset graphic directly onto the targeted ImageView layer.
     * Displays a contextual system toast notification if the matching asset file is not found.
     *
     * @param imageView The target graphic component layout element to update.
     * @param cardId The structural text identifier of the game card to fetch and render.
     */
    private void showCard(
            ImageView imageView,
            String cardId
    ) {
        // Construct the expected asset reference name string based on the convention prefix
        String resourceName =
                "card_" + cardId;

        // Perform a dynamic resource table lookup to retrieve the numeric resource identifier
        int drawableId =
                getResources().getIdentifier(
                        resourceName,
                        "drawable",
                        getPackageName()
                );

        // Fallback error-handling branch if the requested image asset key does not exist
        if (drawableId == 0) {
            // Clear any active image artifacts to avoid displaying outdated card states
            imageView.setImageDrawable(null);

            Toast.makeText(
                    this,
                    "Missing image: " + resourceName,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Apply the newly resolved graphic asset onto the targeted layout layer view
        imageView.setImageResource(drawableId);
    }


    /**
     * Called when the activity is being destroyed by the Android system.
     * Unregisters all WebSocket event listeners from the shared socket instance
     * to clear object references and prevent severe memory leaks.
     */
    @Override
    protected void onDestroy() {
        // Invoke the superclass cleanup routine first
        super.onDestroy();

        // Retrieve the global active socket instance
        Socket socket =
                SocketManager.getInstance().getSocket();

        // Safely detach all assigned gameplay listeners if the socket channel is accessible
        if (socket != null) {
            socket.off("game_state", gameStateListener);
            socket.off("wrong_symbol", wrongSymbolListener);
            socket.off("round_result", roundResultListener);
            socket.off("game_over", gameOverListener);
        }
    }
}