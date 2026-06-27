package me.meydan.dobble;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchHistoryActivity extends AppCompatActivity {
    // List to store all past matches found in the database
    private final List<MatchItem> matches = new ArrayList<>();

    // Map to save opponent names (key: userId, value: userName) so we don't download them twice
    private final Map<String, String> userNames = new HashMap<>();

    // UI elements and adapter for the match history list
    private MatchHistoryAdapter adapter;
    private ProgressBar historyProgressBar;
    private TextView emptyHistoryTextView;

    // Firestore database instance
    private FirebaseFirestore firestore;

    // Counter to keep track of how many matches are still loading
    private int pendingMatches = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_history);

        // Connect to the Firestore database
        firestore = FirebaseFirestore.getInstance();

        // Close this screen when the back button is clicked
        findViewById(R.id.backButton)
                .setOnClickListener(v -> finish());

        // Find the visual views in the layout file
        RecyclerView matchesRecyclerView =
                findViewById(R.id.matchesRecyclerView);

        historyProgressBar =
                findViewById(R.id.historyProgressBar);

        emptyHistoryTextView =
                findViewById(R.id.emptyHistoryTextView);

        // Create the list adapter and connect it to our matches list
        adapter = new MatchHistoryAdapter(matches);

        // Configure the RecyclerView to show items in a standard vertical list
        matchesRecyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        // Link the adapter to the RecyclerView
        matchesRecyclerView.setAdapter(adapter);

        // Start loading the matches data from the database
        loadMatches();
    }

    /**
     * Starts loading the list of matches from the database.
     * It checks if the user is logged in, finds all matches they played,
     * and triggers the loading process for each match found.
     */
    private void loadMatches() {
        // Get the currently logged-in user
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        // If no user is logged in, close this screen immediately
        if (user == null) {
            finish();
            return;
        }

        // Save the unique ID of the current user
        String currentUid = user.getUid();

        // Look into the "matches" database collection
        firestore.collection("matches")
                .whereArrayContains("players", currentUid) // Find matches where this user played
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Clear the old list before adding the new data
                    matches.clear();

                    // If the user has never played any matches, stop loading right here
                    if (querySnapshot.isEmpty()) {
                        finishLoading();
                        return;
                    }

                    // Set the counter to the total number of matches found
                    pendingMatches = querySnapshot.size();

                    // Loop through each match document found in the database
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        loadSingleMatch(document, currentUid);
                    }
                })
                .addOnFailureListener(error -> {
                    // Hide the loading spinner if something goes wrong
                    historyProgressBar.setVisibility(View.GONE);

                    // Show an error pop-up message on the screen
                    Toast.makeText(
                            MatchHistoryActivity.this,
                            error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    /**
     * Reads the data of a single match document from the database.
     * It extracts the players, scores, match time, and figures out who the opponent was
     * and who won the game.
     */
    private void loadSingleMatch(
            QueryDocumentSnapshot document,
            String currentUid
    ) {
        // Get the list of player IDs from the document
        List<String> players =
                (List<String>) document.get("players");

        // Get the scores map from the document
        Map<String, Object> scores =
                (Map<String, Object>) document.get("scores");

        // Get the player names map from the document
        Map<String, Object> playerNames =
                (Map<String, Object>) document.get("playerNames");

        // Get the user ID of the match winner
        String winnerUid =
                document.getString("winnerUid");

        // Safety check: if data is missing or there are less than 2 players, skip this match
        if (players == null
                || scores == null
                || players.size() < 2) {

            onMatchLoaded();
            return;
        }

        // Figure out which player ID belongs to the opponent
        String opponentUid =
                players.get(0).equals(currentUid)
                        ? players.get(1)
                        : players.get(0);

        // Convert and extract the user's score and the opponent's score
        int myScore =
                numberToInt(scores.get(currentUid));

        int opponentScore =
                numberToInt(scores.get(opponentUid));

        // Read the timestamp when the match was created and convert it to a Date object
        Timestamp timestamp =
                document.getTimestamp("createdAt");

        Date createdAt =
                timestamp != null
                        ? timestamp.toDate()
                        : null;

        // Check if the current user is the winner of this match
        boolean won =
                currentUid.equals(winnerUid);

        String storedOpponentName = null;

        // Try to look up the opponent's name inside the match document itself
        if (playerNames != null
                && playerNames.get(opponentUid) != null) {

            storedOpponentName =
                    String.valueOf(
                            playerNames.get(opponentUid)
                    );
        }

        // If the opponent's name is saved inside the match, use it and add the match to the list
        if (storedOpponentName != null
                && !storedOpponentName.trim().isEmpty()
                && !storedOpponentName.equals(opponentUid)) {

            addMatch(
                    storedOpponentName,
                    myScore,
                    opponentScore,
                    won,
                    createdAt
            );

            return;
        }

        // If the opponent's name is not in the match file, look it up from the users collection
        loadOpponentName(
                opponentUid,
                myScore,
                opponentScore,
                won,
                createdAt
        );
    }

    /**
     * Gets the opponent's name from the database.
     * It first checks if the name is already saved in our local map cache.
     * If not, it downloads the name from the "users" collection in Firestore.
     */
    private void loadOpponentName(
            String opponentUid,
            int myScore,
            int opponentScore,
            boolean won,
            Date createdAt
    ) {
        // Cache Check: If we already downloaded this opponent's name before, use it directly
        if (userNames.containsKey(opponentUid)) {
            addMatch(
                    userNames.get(opponentUid),
                    myScore,
                    opponentScore,
                    won,
                    createdAt
            );

            return;
        }

        // Look up the opponent's profile in the "users" collection
        firestore.collection("users")
                .document(opponentUid)
                .get()
                .addOnSuccessListener(document -> {
                    // Try to get their display name first
                    String opponentName =
                            document.getString("displayName");

                    // Fallback 1: If there is no display name, use their email address instead
                    if (opponentName == null
                            || opponentName.trim().isEmpty()) {

                        opponentName =
                                document.getString("email");
                    }

                    // Fallback 2: If email is also missing, call them "Unknown player"
                    if (opponentName == null
                            || opponentName.trim().isEmpty()) {

                        opponentName = "Unknown player";
                    }

                    // Save the name in our local map so we don't have to download it again later
                    userNames.put(
                            opponentUid,
                            opponentName
                    );

                    // Add the complete match details to our list
                    addMatch(
                            opponentName,
                            myScore,
                            opponentScore,
                            won,
                            createdAt
                    );
                })
                .addOnFailureListener(error ->
                        // If the database query fails, add the match with a placeholder name
                        addMatch(
                                "Unknown player",
                                myScore,
                                opponentScore,
                                won,
                                createdAt
                        )
                );
    }

    /**
     * Creates a new MatchItem object and adds it to the match history list.
     * It then tells the app that this specific match has finished loading.
     */
    private void addMatch(
            String opponentName,
            int myScore,
            int opponentScore,
            boolean won,
            Date createdAt
    ) {
        // Create a new match record and add it to our tracking array list
        matches.add(
                new MatchItem(
                        opponentName,
                        myScore,
                        opponentScore,
                        won,
                        createdAt
                )
        );

        // Call the helper method to handle the next steps after a match loads
        onMatchLoaded();
    }

    /**
     * Keeps track of the loading progress for all match documents.
     * When the very last match finishes loading, it sorts the entire list
     * from newest to oldest and updates the user interface.
     */
    private void onMatchLoaded() {
        // Decrease the counter since one match has finished loading
        pendingMatches--;

        // If there are still more matches left to load, stop here and wait for them
        if (pendingMatches > 0) {
            return;
        }

        // Sort the final list so the newest games appear at the top
        matches.sort((first, second) -> {
            // Put matches with missing dates at the very bottom
            if (first.getCreatedAt() == null) {
                return 1;
            }

            if (second.getCreatedAt() == null) {
                return -1;
            }

            // Compare dates to arrange them in descending order (newest first)
            return second.getCreatedAt()
                    .compareTo(first.getCreatedAt());
        });

        // Close the loading process and refresh the screen
        finishLoading();
    }

    /**
     * Closes the loading state once all match items are fully processed.
     * It hides the progress loader spinner, checks if the history list is completely empty
     * to toggle a placeholder text message, and refreshes the adapter list view.
     */
    private void finishLoading() {
        // Hide the loading spinner from the screen
        historyProgressBar.setVisibility(View.GONE);

        // Show the placeholder text if the list is empty, otherwise hide it
        emptyHistoryTextView.setVisibility(
                matches.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        // Tell the list adapter to refresh and display the updated match history items
        adapter.notifyDataSetChanged();
    }

    private int numberToInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return 0;
    }
}