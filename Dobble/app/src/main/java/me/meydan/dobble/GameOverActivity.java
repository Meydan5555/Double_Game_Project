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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

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

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        boolean didWin =
                currentUser != null
                        && currentUser.getUid().equals(winnerUid);

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

        finalScoreTextView.setText(
                firstScore + " - " + secondScore
        );

        playersTextView.setText(
                firstName + " vs " + secondName
        );

        saveStatusTextView.setText(
                statsSaved
                        ? "Result saved to your profile"
                        : "The result could not be saved"
        );

        backHomeButton.setOnClickListener(v ->
                returnHome()
        );
    }

    private void returnHome() {
        Intent intent = new Intent(
                GameOverActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );

        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        returnHome();
    }
}