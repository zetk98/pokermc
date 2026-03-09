package com.pokermc.bang.screen;

/**
 * Bang! UI language. EN = English, VI = Tiếng Việt.
 * Vietnamese sub right below English for easy editing.
 */
public class BangLang {
    public enum Lang { EN, VI }
    private static Lang current = Lang.EN;

    public static Lang get() { return current; }
    public static void set(Lang l) { current = l; }
    public static boolean isVi() { return current == Lang.VI; }

    public static String tr(String en, String vi) {
        return isVi() ? vi : en;
    }

    // GUI strings - English / Vietnamese sub
    public static final String LEAVE = "Leave";
    // Rời bàn

    public static final String SETTINGS = "Settings";
    // Cài đặt

    public static final String LANGUAGE = "Language";
    // Ngôn ngữ

    public static final String END_TURN = "End";
    // Kết thúc lượt

    public static final String PLAY = "Play";
    // Đánh

    public static final String START = "▶";
    // ▶

    public static final String NEW_GAME = "New Game";
    // Ván mới

    public static final String CHOOSE_CARD = "Choose card";
    // Chọn lá

    public static final String USE_MISS = "Use Miss";
    // Dùng Miss

    public static final String TAKE_HIT = "Take hit";
    // Nhận đạn

    public static final String REPLACE_GUN = "Replace gun";
    // Đổi súng

    public static final String EQUIP = "Equip";
    // Trang bị

    public static final String BANG = "Bang";
    // Bang

    public static final String PASS = "Pass";
    // Bỏ qua

    public static final String CLICK_PLAYER_TARGET = "Click player to target";
    // Nhấn người chơi để nhắm

    public static final String CHOOSE_CARD_GIVE = "Choose card to give";
    // Chọn lá để đưa

    public static final String CHOOSE_CARD_DISCARD = "Choose card to discard";
    // Chọn lá để loại

    public static final String JAIL_CHECK = "Jail check - Card drawn:";
    // Kiểm tra Jail - Lá rút:

    public static final String CARDS = " cards";
    // lá

    public static final String GAP = " gap:";
    // khoảng:

    public static final String JAIL = "JAIL";
    // TÙ

    public static final String CHARACTER_SLOT = "Character slot";
    // Ô nhân vật

    /** Translate status/log message to Vietnamese. Server sends English. */
    public static String translateLog(String en) {
        if (!isVi() || en == null || en.isEmpty()) return en;
        // Common patterns - add more as needed
        if (en.contains("Waiting for players")) return en.replace("Waiting for players...", "Đang chờ người chơi...");
        if (en.contains(" joined.")) return en.replace(" joined.", " đã tham gia.");
        if (en.contains(" left (dead).")) return en.replace(" left (dead).", " rời (chết).");
        if (en.contains("Your role...")) return "Vai trò của bạn...";
        if (en.contains("Dealing cards...")) return "Đang chia bài...";
        if (en.contains("Dealing to first player...")) return "Chia cho người đầu...";
        if (en.contains("Your turn, ")) return en.replace("Your turn, ", "Lượt của ").replace("!", "!");
        if (en.contains("'s turn.")) return en.replace("'s turn.", " lượt chơi.");
        if (en.contains(" in Jail! Drawing...")) return en.replace(" in Jail! Drawing...", " trong Tù! Đang rút...");
        if (en.contains(" escaped Jail! (♥)")) return en.replace(" escaped Jail! (♥)", " thoát Tù! (♥)");
        if (en.contains(" failed Jail, skipped turn.")) return en.replace(" failed Jail, skipped turn.", " thất bại Tù, mất lượt.");
        if (en.contains(" gave ")) return en.replace(" gave ", " đưa ").replace(" to ", " cho ");
        if (en.contains(" discarded ")) return en.replace(" discarded ", " loại ");
        if (en.contains("Game over!")) return "Kết thúc ván!";
        if (en.contains(" used Missed! Dodged.")) return en.replace(" used Missed! Dodged.", " dùng Miss! Né.");
        if (en.contains(" took the hit! Lost 1 HP.")) return en.replace(" took the hit! Lost 1 HP.", " nhận đạn! Mất 1 HP.");
        if (en.contains(" hit by Gatling! Use Miss or lose HP.")) return en.replace(" hit by Gatling! Use Miss or lose HP.", " bị Gatling! Dùng Miss hoặc mất HP.");
        if (en.contains("Gatling ended.")) return "Gatling kết thúc.";
        if (en.contains(" lost Duel! Lost 1 HP.")) return en.replace(" lost Duel! Lost 1 HP.", " thua Đấu! Mất 1 HP.");
        if (en.contains(" played Bang in Duel!")) return en.replace(" played Bang in Duel!", " đánh Bang trong Đấu!");
        if (en.contains(" has no Bang, lost Duel! Lost 1 HP.")) return en.replace(" has no Bang, lost Duel! Lost 1 HP.", " không có Bang, thua Đấu! Mất 1 HP.");
        if (en.contains(" shot ")) return en.replace(" shot ", " bắn ").replace(" with Bang!", " bằng Bang!");
        if (en.contains(" choose card to discard")) return en.replace(" choose card to discard", " chọn lá để loại");
        if (en.contains(" choose card to give")) return en.replace(" choose card to give", " chọn lá để đưa");
        if (en.contains(" drank Beer! +1 HP")) return en.replace(" drank Beer! +1 HP", " uống Beer! +1 HP");
        if (en.contains(" drew 2 cards.")) return en.replace(" drew 2 cards.", " rút 2 lá.");
        if (en.contains(" drew 3 cards.")) return en.replace(" drew 3 cards.", " rút 3 lá.");
        if (en.contains(" jailed ")) return en.replace(" jailed ", " bỏ tù ").replace("!", "!");
        if (en.contains(" used Gatling! Shooting everyone.")) return en.replace(" used Gatling! Shooting everyone.", " dùng Gatling! Bắn mọi người.");
        if (en.contains(" challenged ")) return en.replace(" challenged ", " thách đấu ").replace("! (Duel)", "! (Đấu)");
        if (en.contains(" replaced gun with ")) return en.replace(" replaced gun with ", " đổi súng thành ");
        if (en.contains(" equipped ")) return en.replace(" equipped ", " trang bị ");
        if (en.contains(" used Barrel! (♥) Dodged.")) return en.replace(" used Barrel! (♥) Dodged.", " dùng Barrel! (♥) Né.");
        if (en.contains(" used Barrel (no ♥). Use Miss/Beer or take hit.")) return en.replace(" used Barrel (no ♥). Use Miss/Beer or take hit.", " dùng Barrel (không ♥). Dùng Miss/Beer hoặc nhận đạn.");
        if (en.contains(" hit by Indians! Play Bang or lose 1 HP.")) return en.replace(" hit by Indians! Play Bang or lose 1 HP.", " bị Indians! Đánh Bang hoặc mất 1 HP.");
        if (en.contains(" played Bang! Dodged Indians.")) return en.replace(" played Bang! Dodged Indians.", " đánh Bang! Né Indians.");
        if (en.contains(" has no Bang! Lost 1 HP.")) return en.replace(" has no Bang! Lost 1 HP.", " không có Bang! Mất 1 HP.");
        if (en.contains("Indians ended.")) return "Indians kết thúc.";
        if (en.contains(" hit by Gatling! Use Miss, Barrel, or lose HP.")) return en.replace(" hit by Gatling! Use Miss, Barrel, or lose HP.", " bị Gatling! Dùng Miss, Barrel hoặc mất HP.");
        if (en.contains("Dynamite exploded")) return en.replace("Dynamite exploded", "Dynamite nổ");
        return en;
    }
}
