package com.pokermc.bang.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bang! character definitions. ID 1-16 maps to texture 001-016.
 * Each character has fixed HP and a unique ability.
 */
public final class BangCharacter {

    public static final int MIN_ID = 1;
    public static final int MAX_ID = 16;

    public record CharData(int id, String name, int hp, String ability) {
        public String textureCode() {
            return String.format("character:%03d", id);
        }
    }

    private static final Map<Integer, CharData> BY_ID = Map.ofEntries(
            entry(1, "Paul Regret", 3, "Others see you +1 distance (like Mustang)"),
            entry(2, "El Gringo", 3, "When damaged: draw 1 from attacker's hand"),
            entry(3, "Vulture Sam", 4, "When someone dies: take all their cards"),
            entry(4, "Calamity Janet", 4, "Use BANG as MISS and vice versa"),
            entry(5, "Black Jack", 4, "Draw: reveal 2nd card. If Hearts/Diamonds → draw +1"),
            entry(6, "Willy The Kid", 4, "Unlimited Bang per turn"),
            entry(7, "Lucky Duke", 4, "When judging: draw 2, pick 1, discard both"),
            entry(8, "Kit Carlson", 4, "Each turn: draw 3, keep 2"),
            entry(9, "Rose Doolan", 4, "You see others -1 distance (like Appaloosa)"),
            entry(10, "Suzy Lafayette", 4, "When hand empty: draw 1"),
            entry(11, "Bart Cassidy", 4, "When lose 1 HP: draw 1"),
            entry(12, "Jesse Jones", 4, "Draw phase: may take 1st card from a player's hand"),
            entry(13, "Slab The Killer", 4, "Target needs 2 Miss to dodge your Bang"),
            entry(14, "Sid Ketchum", 4, "Discard 2 cards to heal 1 HP"),
            entry(15, "Jourdonnais", 4, "When Bang target: draw 1; if Hearts → dodge"),
            entry(16, "Pedro Ramirez", 4, "Draw phase: may take top of discard pile")
    );

    private static Map.Entry<Integer, CharData> entry(int id, String name, int hp, String ability) {
        return Map.entry(id, new CharData(id, name, hp, ability));
    }

    public static CharData get(int id) {
        return BY_ID.get(id);
    }

    public static boolean isValidId(int id) {
        return id >= MIN_ID && id <= MAX_ID;
    }

    /** Get 3 random character IDs (no duplicates, excluding already chosen). */
    public static int[] pickThreeOptions(List<Integer> alreadyChosen) {
        List<Integer> pool = new ArrayList<>();
        for (int i = MIN_ID; i <= MAX_ID; i++) {
            if (!alreadyChosen.contains(i)) pool.add(i);
        }
        if (pool.size() < 3) return new int[0];
        Collections.shuffle(pool);
        return new int[]{ pool.get(0), pool.get(1), pool.get(2) };
    }
}
