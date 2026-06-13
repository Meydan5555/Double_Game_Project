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

public class MatchHistoryAdapter
        extends RecyclerView.Adapter<MatchHistoryAdapter.MatchViewHolder> {

    private final List<MatchItem> matches;

    public MatchHistoryAdapter(List<MatchItem> matches) {
        this.matches = matches;
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_match,
                        parent,
                        false
                );

        return new MatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MatchViewHolder holder,
            int position
    ) {
        MatchItem match = matches.get(position);

        holder.resultTextView.setText(
                match.isWon() ? "VICTORY" : "DEFEAT"
        );

        holder.resultTextView.setTextColor(
                holder.itemView.getContext().getColor(
                        match.isWon()
                                ? R.color.dobble_purple
                                : android.R.color.holo_red_dark
                )
        );

        holder.opponentTextView.setText(
                "Against: " + match.getOpponentName()
        );

        holder.matchScoreTextView.setText(
                "Score: "
                        + match.getMyScore()
                        + " - "
                        + match.getOpponentScore()
        );

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
            holder.matchDateTextView.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    static class MatchViewHolder
            extends RecyclerView.ViewHolder {

        TextView resultTextView;
        TextView opponentTextView;
        TextView matchScoreTextView;
        TextView matchDateTextView;

        public MatchViewHolder(@NonNull View itemView) {
            super(itemView);

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