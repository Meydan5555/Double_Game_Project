package com.example.doublegame;

public class Card {
    private int[] symbols = new int[8];

    public boolean isInCard(int symbolId) {
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] == symbolId) {
                return true;
            }
        }
        return false;
    }
}
