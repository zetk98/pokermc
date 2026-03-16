package com.pokermc.bang.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bang! card type. Each card has typeId and rankSuit for texture lookup.
 */
public record BangCard(String typeId, String rankSuit) {

    public static final String BANG = "bang";
    public static final String MISSED = "missed";
    public static final String BEER = "beer";
    public static final String PANIC = "panic";
    public static final String CAT_BALOU = "cat_balou";
    public static final String STAGECOACH = "stagecoach";
    public static final String WELLS_FARGO = "wells_fargo";
    public static final String GATLING = "gatling";
    public static final String INDIANS = "indians";
    public static final String DUEL = "duel";
    public static final String GENERAL_STORE = "general_store";
    public static final String SALOON = "saloon";
    public static final String REMINGTON = "remington";
    public static final String REV_CARBINE = "rev_carbine";
    public static final String SCHOFIELD = "schofield";
    public static final String VOLCANIC = "volcanic";
    public static final String WINCHESTER = "winchester";
    public static final String MUSTANG = "mustang";
    public static final String APPALOOSA = "appaloosa";
    public static final String BARREL = "barrel";
    public static final String DYNAMITE = "dynamite";
    public static final String JAIL = "jail";

    public String toCode() { return typeId + ":" + rankSuit; }

    /** Blue equipment cards. */
    public boolean isBlue() {
        return switch (typeId) {
            case SCHOFIELD, VOLCANIC, REMINGTON, REV_CARBINE, WINCHESTER,
                 MUSTANG, APPALOOSA, BARREL, DYNAMITE -> true;
            default -> false;
        };
    }

    /** Equipment category for no-duplicate check. Guns share "gun", others use typeId. */
    public String getEquipmentType() {
        return switch (typeId) {
            case SCHOFIELD, VOLCANIC, REMINGTON, REV_CARBINE, WINCHESTER -> "gun";
            default -> typeId;
        };
    }

    public boolean isGun() {
        return "gun".equals(getEquipmentType());
    }

    /** Hearts suit (Cơ) - last char H. */
    public boolean isHearts() {
        return rankSuit != null && rankSuit.endsWith("H");
    }

    /** Diamonds suit (Rô) - last char D. */
    public boolean isDiamonds() {
        return rankSuit != null && rankSuit.endsWith("D");
    }

    /** Hearts or Diamonds (for Black Jack character). */
    public boolean isHeartsOrDiamonds() {
        return isHearts() || isDiamonds();
    }

    /** Spades 2-9 (Bích 2-9) for Dynamite. */
    public boolean isSpades2to9() {
        if (rankSuit == null || !rankSuit.endsWith("S")) return false;
        String r = rankSuit.substring(0, rankSuit.length() - 1);
        return r.matches("[2-9]");
    }

    public static BangCard fromCode(String code) {
        if (code == null || !code.contains(":")) return null;
        int colon = code.indexOf(':');
        return new BangCard(code.substring(0, colon), code.substring(colon + 1));
    }

    /** Build standard Bang! deck. */
    public static List<BangCard> buildDeck() {
        List<BangCard> deck = new ArrayList<>();
        add(deck, BANG, 25, "AS","AH","KH","QH","AD","2D","3D","4D","5D","6D","7D","8D","9D","TD","JD","QD","KD","2C","3C","4C","5C","6C","7C","8C","9C");
        add(deck, MISSED, 12, "2S","3S","4S","5S","6S","7S","8S","10C","JC","QC","KC","AC");
        add(deck, BEER, 6, "6H","7H","8H","9H","TH","JH");
        add(deck, PANIC, 4, "JH","QH","AH","8D");
        add(deck, CAT_BALOU, 4, "9D","TD","JD","KH");
        add(deck, STAGECOACH, 2, "9S","9C");
        add(deck, WELLS_FARGO, 1, "3H");
        add(deck, GATLING, 1, "10H");
        add(deck, INDIANS, 2, "KD","AD");
        add(deck, DUEL, 3, "JS","8C","QD");
        add(deck, GENERAL_STORE, 2, "QS","JS");
        add(deck, SALOON, 1, "5H");
        add(deck, REMINGTON, 1, "JS");
        add(deck, REV_CARBINE, 1, "AD");
        add(deck, SCHOFIELD, 3, "QS","KC","JS");
        add(deck, VOLCANIC, 2, "10C","10S");
        add(deck, WINCHESTER, 1, "8S");
        add(deck, MUSTANG, 2, "8H","9H");
        add(deck, APPALOOSA, 1, "AC");
        add(deck, BARREL, 2, "QH","KH");
        add(deck, DYNAMITE, 1, "2S");
        add(deck, JAIL, 3, "JS","10S","4H");
        Collections.shuffle(deck);
        return deck;
    }

    private static void add(List<BangCard> deck, String type, int count, String... rankSuits) {
        for (int i = 0; i < count; i++) {
            deck.add(new BangCard(type, rankSuits[i % rankSuits.length]));
        }
    }
}
