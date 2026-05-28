package com.example.doublegame;
import java.util.Queue;

public class GameManager {
    private Queue<Card> playerCards;
    private Queue<Card> rivalCards;


    public GameManager(Queue<Card> pCards, Queue<Card> rCards) {
        playerCards = pCards;
        rivalCards = rCards;
    }
    public Card getNextPlayerCard() {
        return playerCards.peek();
    }
    public Card getNextRivalCard() {
        return rivalCards.peek();
    }
    public boolean checkAnswer(int player, int symbolId) {
        if (player == 1) {
            if (rivalCards.peek().isInCard(symbolId)) {
                playerCards.add(playerCards.remove());
                playerCards.add(rivalCards.remove());
                return true;
            }
            rivalCards.add(playerCards.remove());
            rivalCards.add(rivalCards.remove());
            return false;
        }
        if (playerCards.peek().isInCard(symbolId)) {
            playerCards.add(playerCards.remove());
            playerCards.add(rivalCards.remove());
            return true;
        }
        rivalCards.add(playerCards.remove());
        rivalCards.add(rivalCards.remove());
        return false;
    }
    public void playerWon() {
        playerCards.add(playerCards.remove());
        playerCards.add(rivalCards.remove());
    }
    public void playerLost() {
        rivalCards.add(playerCards.remove());
        rivalCards.add(rivalCards.remove());
    }
    public boolean checkIfPlayerLost() {
        return playerCards.isEmpty(); // if player is lost return true
    }
}
