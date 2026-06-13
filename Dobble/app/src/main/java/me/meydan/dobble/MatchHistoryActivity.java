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

    private final List<MatchItem> matches = new ArrayList<>();
    private final Map<String, String> userNames = new HashMap<>();

    private MatchHistoryAdapter adapter;
    private ProgressBar historyProgressBar;
    private TextView emptyHistoryTextView;

    private FirebaseFirestore firestore;

    private int pendingMatches = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_history);

        firestore = FirebaseFirestore.getInstance();

        findViewById(R.id.backButton)
                .setOnClickListener(v -> finish());

        RecyclerView matchesRecyclerView =
                findViewById(R.id.matchesRecyclerView);

        historyProgressBar =
                findViewById(R.id.historyProgressBar);

        emptyHistoryTextView =
                findViewById(R.id.emptyHistoryTextView);

        adapter = new MatchHistoryAdapter(matches);

        matchesRecyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        matchesRecyclerView.setAdapter(adapter);

        loadMatches();
    }

    private void loadMatches() {
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        String currentUid = user.getUid();

        firestore.collection("matches")
                .whereArrayContains("players", currentUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    matches.clear();

                    if (querySnapshot.isEmpty()) {
                        finishLoading();
                        return;
                    }

                    pendingMatches = querySnapshot.size();

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        loadSingleMatch(document, currentUid);
                    }
                })
                .addOnFailureListener(error -> {
                    historyProgressBar.setVisibility(View.GONE);

                    Toast.makeText(
                            MatchHistoryActivity.this,
                            error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void loadSingleMatch(
            QueryDocumentSnapshot document,
            String currentUid
    ) {
        List<String> players =
                (List<String>) document.get("players");

        Map<String, Object> scores =
                (Map<String, Object>) document.get("scores");

        Map<String, Object> playerNames =
                (Map<String, Object>) document.get("playerNames");

        String winnerUid =
                document.getString("winnerUid");

        if (players == null
                || scores == null
                || players.size() < 2) {

            onMatchLoaded();
            return;
        }

        String opponentUid =
                players.get(0).equals(currentUid)
                        ? players.get(1)
                        : players.get(0);

        int myScore =
                numberToInt(scores.get(currentUid));

        int opponentScore =
                numberToInt(scores.get(opponentUid));

        Timestamp timestamp =
                document.getTimestamp("createdAt");

        Date createdAt =
                timestamp != null
                        ? timestamp.toDate()
                        : null;

        boolean won =
                currentUid.equals(winnerUid);

        String storedOpponentName = null;

        if (playerNames != null
                && playerNames.get(opponentUid) != null) {

            storedOpponentName =
                    String.valueOf(
                            playerNames.get(opponentUid)
                    );
        }

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

        loadOpponentName(
                opponentUid,
                myScore,
                opponentScore,
                won,
                createdAt
        );
    }

    private void loadOpponentName(
            String opponentUid,
            int myScore,
            int opponentScore,
            boolean won,
            Date createdAt
    ) {
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

        firestore.collection("users")
                .document(opponentUid)
                .get()
                .addOnSuccessListener(document -> {
                    String opponentName =
                            document.getString("displayName");

                    if (opponentName == null
                            || opponentName.trim().isEmpty()) {

                        opponentName =
                                document.getString("email");
                    }

                    if (opponentName == null
                            || opponentName.trim().isEmpty()) {

                        opponentName = "Unknown player";
                    }

                    userNames.put(
                            opponentUid,
                            opponentName
                    );

                    addMatch(
                            opponentName,
                            myScore,
                            opponentScore,
                            won,
                            createdAt
                    );
                })
                .addOnFailureListener(error ->
                        addMatch(
                                "Unknown player",
                                myScore,
                                opponentScore,
                                won,
                                createdAt
                        )
                );
    }

    private void addMatch(
            String opponentName,
            int myScore,
            int opponentScore,
            boolean won,
            Date createdAt
    ) {
        matches.add(
                new MatchItem(
                        opponentName,
                        myScore,
                        opponentScore,
                        won,
                        createdAt
                )
        );

        onMatchLoaded();
    }

    private void onMatchLoaded() {
        pendingMatches--;

        if (pendingMatches > 0) {
            return;
        }

        matches.sort((first, second) -> {
            if (first.getCreatedAt() == null) {
                return 1;
            }

            if (second.getCreatedAt() == null) {
                return -1;
            }

            return second.getCreatedAt()
                    .compareTo(first.getCreatedAt());
        });

        finishLoading();
    }

    private void finishLoading() {
        historyProgressBar.setVisibility(View.GONE);

        emptyHistoryTextView.setVisibility(
                matches.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        adapter.notifyDataSetChanged();
    }

    private int numberToInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return 0;
    }
}