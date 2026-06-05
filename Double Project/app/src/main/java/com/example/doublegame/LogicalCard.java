package com.example.doublegame;

public class LogicalCard {
        private final int[] symbolIds;

        public LogicalCard(int[] symbolIds) {

            this.symbolIds = symbolIds;
        }

        public int getSymbolAt(int index) {
            return symbolIds[index];
        }

        public int size() {
            return symbolIds.length;
        }
    }

