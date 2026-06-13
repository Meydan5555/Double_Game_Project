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

    private void sendSymbolClick(String symbol) {
        Socket socket =
                SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            showToast("Not connected to server");
            return;
        }

        clickEnabled = false;

        JSONObject data = new JSONObject();

        try {
            data.put(
                    "cardId",
                    currentPlayerCardId
            );

            data.put(
                    "symbol",
                    symbol
            );

        } catch (Exception error) {
            clickEnabled = true;
            return;
        }

        socket.emit(
                "symbol_click",
                new Object[]{data},
                (Ack) args -> {
                    if (args.length == 0 || args[0] == null) {
                        runOnUiThread(() -> {
                            clickEnabled = true;
                            showToast("Empty server response");
                        });

                        return;
                    }

                    try {
                        JSONObject response;

                        if (args[0] instanceof JSONObject) {
                            response =
                                    (JSONObject) args[0];
                        } else {
                            response =
                                    new JSONObject(
                                            args[0].toString()
                                    );
                        }

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

                        if (!correct) {
                            runOnUiThread(() ->
                                    clickEnabled = true
                            );
                        }

                    } catch (Exception error) {
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

    private void registerSocketListeners() {
        Socket socket =
                SocketManager.getInstance().getSocket();

        if (socket == null || !socket.connected()) {
            Toast.makeText(
                    this,
                    "Not connected to server",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        socket.on("game_state", gameStateListener);
        socket.on("wrong_symbol", wrongSymbolListener);
        socket.on("round_result", roundResultListener);
        socket.on("game_over", gameOverListener);
        socket.emit(
                "get_game_state",
                new Object[]{new JSONObject()},
                (Ack) args -> {
                    if (args.length == 0 || args[0] == null) {
                        return;
                    }

                    try {
                        JSONObject response;

                        if (args[0] instanceof JSONObject) {
                            response = (JSONObject) args[0];
                        } else {
                            response =
                                    new JSONObject(args[0].toString());
                        }

                        if (!response.optBoolean("ok", false)) {
                            return;
                        }

                        JSONObject game =
                                response.optJSONObject("game");

                        if (game != null) {
                            runOnUiThread(() ->
                                    updateGameState(game)
                            );
                        }

                    } catch (Exception ignored) {
                    }
                }
        );
    }

    private void openGameOver(JSONObject gameOver) {
        try {
            JSONObject scores =
                    gameOver.getJSONObject("scores");

            org.json.JSONArray players =
                    gameOver.getJSONArray("players");

            JSONObject firstPlayer =
                    players.getJSONObject(0);

            JSONObject secondPlayer =
                    players.getJSONObject(1);

            String firstUid =
                    firstPlayer.getString("uid");

            String secondUid =
                    secondPlayer.getString("uid");

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

            int firstScore =
                    scores.optInt(firstUid, 0);

            int secondScore =
                    scores.optInt(secondUid, 0);

            Intent intent = new Intent(
                    GameActivity.this,
                    GameOverActivity.class
            );

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

            Object statsSavedValue =
                    gameOver.opt("statsSaved");

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

            startActivity(intent);
            finish();

        } catch (Exception error) {
            showToast(
                    "Could not open game-over screen"
            );
        }
    }

    private void updateGameState(JSONObject state) {
        String centerCardId =
                state.optString("centerCardId");

        String playerCardId =
                state.optString("playerCardId");


        currentPlayerCardId = playerCardId;
        clickEnabled = true;

        int roundNumber =
                state.optInt("roundNumber", 1);

        roundTextView.setText(
                "Round " + roundNumber
        );

        JSONObject scores =
                state.optJSONObject("scores");

        if (scores != null) {
            String[] keys = new String[2];
            int index = 0;

            java.util.Iterator<String> iterator =
                    scores.keys();

            while (iterator.hasNext() && index < 2) {
                keys[index] = iterator.next();
                index++;
            }

            if (index == 2) {
                int firstScore =
                        scores.optInt(keys[0], 0);

                int secondScore =
                        scores.optInt(keys[1], 0);

                scoreTextView.setText(
                        firstScore + " - " + secondScore
                );
            }
        }

        showCard(
                centerCardImageView,
                centerCardId
        );

        showCard(
                playerCardImageView,
                playerCardId
        );

        gameStatusTextView.setText(
                "Find the matching symbol!"
        );

        gameStatusTextView.setTextColor(
                getColor(R.color.dobble_text_dark)
        );
    }

    private void showCard(
            ImageView imageView,
            String cardId
    ) {
        String resourceName =
                "card_" + cardId;

        int drawableId =
                getResources().getIdentifier(
                        resourceName,
                        "drawable",
                        getPackageName()
                );

        if (drawableId == 0) {
            imageView.setImageDrawable(null);

            Toast.makeText(
                    this,
                    "Missing image: " + resourceName,
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        imageView.setImageResource(drawableId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Socket socket =
                SocketManager.getInstance().getSocket();

        if (socket != null) {
            socket.off("game_state", gameStateListener);
            socket.off("wrong_symbol", wrongSymbolListener);
            socket.off("round_result", roundResultListener);
            socket.off("game_over", gameOverListener);
        }
    }
}