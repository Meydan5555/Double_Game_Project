package me.meydan.dobble;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class GameOverActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Invoke the superclass layout configuration routine
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        // Bind layout view components to their respective Java object references
        TextView resultTitleTextView =
                findViewById(R.id.resultTitleTextView);

        TextView resultSubtitleTextView =
                findViewById(R.id.resultSubtitleTextView);

        TextView finalScoreTextView =
                findViewById(R.id.finalScoreTextView);

        TextView playersTextView =
                findViewById(R.id.playersTextView);

        TextView saveStatusTextView =
                findViewById(R.id.saveStatusTextView);

        Button backHomeButton =
                findViewById(R.id.backHomeButton);

        // Extract metadata configurations passed through the transition Intent pipeline
        String winnerUid =
                getIntent().getStringExtra("winnerUid");

        String firstUid =
                getIntent().getStringExtra("firstUid");

        String secondUid =
                getIntent().getStringExtra("secondUid");

        String firstName =
                getIntent().getStringExtra("firstName");

        String secondName =
                getIntent().getStringExtra("secondName");

        int firstScore =
                getIntent().getIntExtra("firstScore", 0);

        int secondScore =
                getIntent().getIntExtra("secondScore", 0);

        boolean statsSaved =
                getIntent().getBooleanExtra(
                        "statsSaved",
                        false
                );

        // Retrieve the currently authenticated session user profile from Firebase
        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        // Determine outcome status by comparing the local user's UID against the declared winner ID
        boolean didWin =
                currentUser != null
                        && currentUser.getUid().equals(winnerUid);

        // Update title and subtitle textual indicators based on the calculated game outcome
        if (didWin) {
            resultTitleTextView.setText("YOU WON!");
            resultSubtitleTextView.setText(
                    "You found the symbols first!"
            );
        } else {
            resultTitleTextView.setText("YOU LOST");
            resultSubtitleTextView.setText(
                    "Better luck next game!"
            );
        }

        // Render total points scored during the active gameplay turns
        finalScoreTextView.setText(
                firstScore + " - " + secondScore
        );

        // Populate match contestant identities into the scoreboard layer
        playersTextView.setText(
                firstName + " vs " + secondName
        );

        // Display database synchronization status to notify the player if stats were safely archived
        saveStatusTextView.setText(
                statsSaved
                        ? "Result saved to your profile"
                        : "The result could not be saved"
        );

        // Attach a click event handler to intercept exit requests and navigate home
        backHomeButton.setOnClickListener(v ->
                returnHome()
        );
    }

    /**
     * Constructs a clean navigation Intent redirecting back to MainActivity.
     * Clears the active activity backstack history to prevent players from accidentally
     * navigating backward into a closed game session.
     */
    private void returnHome() {
        Intent intent = new Intent(
                GameOverActivity.this,
                MainActivity.class
        );

        // Inject configuration flags to reset the activity navigation history stack trees
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );

        // Execute task routing operation and terminate the current context layer immediately
        startActivity(intent);
        finish();
    }

    /**
     * Intercepts physical system hardware back button presses.
     * Overrides the default behavior to enforce clean navigation rules via returnHome().
     */
    @Override
    public void onBackPressed() {
        returnHome();
    }
}
