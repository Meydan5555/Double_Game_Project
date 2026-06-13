package me.meydan.dobble;

import java.util.Date;

public class MatchItem {

    private final String opponentName;
    private final int myScore;
    private final int opponentScore;
    private final boolean won;
    private final Date createdAt;

    public MatchItem(
            String opponentName,
            int myScore,
            int opponentScore,
            boolean won,
            Date createdAt
    ) {
        this.opponentName = opponentName;
        this.myScore = myScore;
        this.opponentScore = opponentScore;
        this.won = won;
        this.createdAt = createdAt;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public int getMyScore() {
        return myScore;
    }

    public int getOpponentScore() {
        return opponentScore;
    }

    public boolean isWon() {
        return won;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}