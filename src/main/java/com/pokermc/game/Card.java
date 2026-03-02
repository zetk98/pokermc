package com.pokermc.game;

public record Card(Rank rank, Suit suit) {

    public enum Rank {
        TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"),
        SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "T"),
        JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");

        public final int value;
        public final String symbol;

        Rank(int value, String symbol) {
            this.value = value;
            this.symbol = symbol;
        }

        public static Rank fromSymbol(String s) {
            for (Rank r : values()) {
                if (r.symbol.equals(s)) return r;
            }
            throw new IllegalArgumentException("Unknown rank symbol: " + s);
        }
    }

    public enum Suit {
        SPADES("S", "\u2660", 0x555555),
        HEARTS("H", "\u2665", 0xCC0000),
        DIAMONDS("D", "\u2666", 0xCC0000),
        CLUBS("C", "\u2663", 0x555555);

        public final String code;
        public final String symbol;
        public final int color;

        Suit(String code, String symbol, int color) {
            this.code = code;
            this.symbol = symbol;
            this.color = color;
        }

        public static Suit fromCode(String s) {
            for (Suit suit : values()) {
                if (suit.code.equals(s)) return suit;
            }
            throw new IllegalArgumentException("Unknown suit code: " + s);
        }
    }

    /** Serialize to 2-char code e.g. "AS", "TH", "2D" */
    public String toCode() {
        return rank.symbol + suit.code;
    }

    /** Human-readable e.g. "A♠", "T♥" */
    public String toDisplay() {
        return rank.symbol + suit.symbol;
    }

    /** Blackjack value: 2-10 face, J/Q/K=10, A=11 */
    public int getBlackjackValue() {
        return switch (rank) {
            case TWO -> 2;
            case THREE -> 3;
            case FOUR -> 4;
            case FIVE -> 5;
            case SIX -> 6;
            case SEVEN -> 7;
            case EIGHT -> 8;
            case NINE -> 9;
            case TEN, JACK, QUEEN, KING -> 10;
            case ACE -> 11;
        };
    }

    public static Card fromCode(String code) {
        if (code == null || code.length() < 2) throw new IllegalArgumentException("Bad code: " + code);
        // Rank symbol can be 1 char (2-9, T, J, Q, K, A)
        String rankSym = code.substring(0, code.length() - 1);
        String suitCode = code.substring(code.length() - 1);
        return new Card(Rank.fromSymbol(rankSym), Suit.fromCode(suitCode));
    }
}
