package com.example.doublegame;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private DobbleCardView topCardView;
    private DobbleCardView bottomCardView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        topCardView = findViewById(R.id.topCardView);
        bottomCardView = findViewById(R.id.bottomCardView);

        LogicalCard topCard = new LogicalCard(new int[]{0, 2, 4, 6, 8, 10, 12, 14});
        LogicalCard bottomCard = new LogicalCard(new int[]{1, 3, 5, 7, 9, 11, 13, 6});

        topCardView.setSymbolsClickable(true);
        bottomCardView.setSymbolsClickable(true);

        topCardView.setLogicalCard(topCard);
        bottomCardView.setLogicalCard(bottomCard);


        topCardView.setOnSymbolClickListener(symbolId -> {
        Toast.makeText(this, "Clicked symbol: " + symbolId, Toast.LENGTH_SHORT).show();

        // here you pass symbolId to your game logic or websocket
    });
    }
}