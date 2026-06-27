package me.meydan.dobble;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * This adapter takes a list of past matches and displays them in a RecyclerView list.
 * It shows if the user won or lost, the opponent's name, the score, and the date.
 */
public class MatchHistoryAdapter
        extends RecyclerView.Adapter<MatchHistoryAdapter.MatchViewHolder> {

    // The list of matches to display
    private final List<MatchItem> matches;

    /**
     * Constructor to save the list of matches.
     */
    public MatchHistoryAdapter(List<MatchItem> matches) {
        this.matches = matches;
    }

    /**
     * This method creates a new visual row item (inflates the XML layout) when needed.
     */
    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        // Load the item_match layout file
        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_match,
                        parent,
                        false
                );

        return new MatchViewHolder(view);
    }

    /**
     * This method puts the data from the match list into the text fields of the row.
     */
    @Override
    public void onBindViewHolder(
            @NonNull MatchViewHolder holder,
            int position
    ) {
        // Get the specific match for the current position
        MatchItem match = matches.get(position);

        // Show "VICTORY" if won, or "DEFEAT" if lost
        holder.resultTextView.setText(
                match.isWon() ? "VICTORY" : "DEFEAT"
        );

        // Set color to purple for a win, and red for a loss
        holder.resultTextView.setTextColor(
                holder.itemView.getContext().getColor(
                        match.isWon()
                                ? R.color.dobble_purple
                                : android.R.color.holo_red_dark
                )
        );

        // Set the opponent's name
        holder.opponentTextView.setText(
                "Against: " + match.getOpponentName()
        );

        // Set the final game score
        holder.matchScoreTextView.setText(
                "Score: "
                        + match.getMyScore()
                        + " - "
                        + match.getOpponentScore()
        );

        // Format and set the date if it exists
        if (match.getCreatedAt() != null) {
            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            Locale.getDefault()
                    );

            holder.matchDateTextView.setText(
                    formatter.format(match.getCreatedAt())
            );
        } else {
            // Clear text if there is no date info
            holder.matchDateTextView.setText("");
        }
    }

    /**
     * Returns the total number of items in the list.
     */
    @Override
    public int getItemCount() {
        return matches.size();
    }

    /**
     * This class finds and holds all the text views for a single list item row.
     */
    static class MatchViewHolder
            extends RecyclerView.ViewHolder {

        TextView resultTextView;
        TextView opponentTextView;
        TextView matchScoreTextView;
        TextView matchDateTextView;

        public MatchViewHolder(@NonNull View itemView) {
            super(itemView);

            // Connect variables to the actual layout XML views
            resultTextView =
                    itemView.findViewById(
                            R.id.resultTextView
                    );

            opponentTextView =
                    itemView.findViewById(
                            R.id.opponentTextView
                    );

            matchScoreTextView =
                    itemView.findViewById(
                            R.id.matchScoreTextView
                    );

            matchDateTextView =
                    itemView.findViewById(
                            R.id.matchDateTextView
                    );
        }
    }
}
