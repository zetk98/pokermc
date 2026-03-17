package com.pokermc.taixiu.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokermc.PokerMod;
import com.pokermc.taixiu.game.TaiXiuGameManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Tai Xiu (Sic Bo) Table Screen - Main betting interface.
 */
public class TaiXiuTableScreen extends Screen {

    // Colors
    private static final int BG_DARK = 0xFF1A1A2E;
    private static final int BORDER_GOLD = 0xFFD4AF37;
    private static final int COLOR_ORANGE = 0xFFFF6600; // Highlight color
    private static final int COLOR_GREEN = 0xFF00C853;
    private static final int COLOR_RED = 0xFFFF1744;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_AQUA = 0xFF00FFFF;

    private final net.minecraft.util.math.BlockPos pos;
    private JsonObject stateJson;
    private int playerBalance = 0;
    private String playerBetString = "";
    private List<String> playerBets = new ArrayList<>();

    // Game state
    private TaiXiuGameManager.GameState gameState = TaiXiuGameManager.GameState.BETTING;
    private int die1 = 1, die2 = 1, die3 = 1;
    private int lastDie1 = 1, lastDie2 = 1, lastDie3 = 1;
    private int lastTotal = 3;
    private boolean lastIsTriple = false;
    private String lastResultString = "";

    // Timing - direct from server (no local countdown)
    private int secondsUntilRoll = 0;
    private int secondsUntilNextBet = 0;

    // Betting
    private TextFieldWidget betAmountInput;
    private int betAmount = 10;
    private String selectedBetType = null; // "TAI", "XIU", "ODD", "EVEN", "PAIR", "TRIPLE", "1"-"6"
    private int selectedNumber = -1; // For number bets

    // Layout
    private int guiX, guiY;
    private static final int GUI_W = 300;
    private static final int GUI_H = 250;

    // History
    private List<DiceResult> history = new ArrayList<>();

    // Player bet history (win/loss records)
    private List<PlayerBetResultItem> playerBetHistory = new ArrayList<>();

    // Button positions for highlighting
    private int taiBtnX, taiBtnY, taiBtnW, taiBtnH;
    private int xiuBtnX, xiuBtnY, xiuBtnW, xiuBtnH;
    private int oddBtnX, oddBtnY, oddBtnW, oddBtnH;
    private int evenBtnX, evenBtnY, evenBtnW, evenBtnH;
    private int pairBtnX, pairBtnY, pairBtnW, pairBtnH;
    private int tripleBtnX, tripleBtnY, tripleBtnW, tripleBtnH;
    private int[] numBtnX = new int[6], numBtnY = new int[6], numBtnW = new int[6], numBtnH = new int[6];

    // Help button bounds for tooltip
    private int helpBtnX, helpBtnY, helpBtnW, helpBtnH;

    public TaiXiuTableScreen(net.minecraft.util.math.BlockPos pos, String initialStateJson) {
        super(Text.literal("Tai Xiu - Sic Bo"));
        this.pos = pos;
        updateState(initialStateJson);
    }

    public net.minecraft.util.math.BlockPos getPos() { return pos; }

    @Override
    protected void init() {
        super.init();

        guiX = (width - GUI_W) / 2;
        guiY = (height - GUI_H) / 2;

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TABLE").formatted(Formatting.GOLD),
                btn -> {} // Already on table
        ).dimensions(guiX + 5, guiY + 5, 45, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("HISTORY").formatted(Formatting.AQUA),
                btn -> client.setScreen(new TaiXiuHistoryScreen(pos, stateJson != null ? stateJson.toString() : "{}"))
        ).dimensions(guiX + 55, guiY + 5, 55, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("MY BETS").formatted(Formatting.LIGHT_PURPLE),
                btn -> client.setScreen(new TaiXiuMyBetsScreen(pos, stateJson != null ? stateJson.toString() : "{}"))
        ).dimensions(guiX + 115, guiY + 5, 65, 18).build());

        // Help button (?) for payout info
        addDrawableChild(ButtonWidget.builder(
                Text.literal("?").formatted(Formatting.YELLOW),
                btn -> {} // Tooltip shown on hover
        ).dimensions(guiX + 240, guiY + 5, 20, 18).build());
        helpBtnX = guiX + 240;
        helpBtnY = guiY + 5;
        helpBtnW = 20;
        helpBtnH = 18;

        // Close button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✕").formatted(Formatting.RED),
                btn -> close()
        ).dimensions(guiX + 265, guiY + 5, 25, 18).build());

        // Bet amount input
        betAmountInput = new TextFieldWidget(textRenderer, guiX + 100, guiY + 215, 50, 18, Text.literal("Bet"));
        betAmountInput.setText(String.valueOf(betAmount));
        betAmountInput.setChangedListener(text -> {
            try {
                betAmount = Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {}
        });
        betAmountInput.setFocused(true);
        addDrawableChild(betAmountInput);

        // MAX button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("MAX").formatted(Formatting.GOLD),
                btn -> { betAmount = playerBalance; betAmountInput.setText(String.valueOf(playerBalance)); }
        ).dimensions(guiX + 155, guiY + 215, 35, 18).build());

        // CONFIRM button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("CONFIRM").formatted(Formatting.GREEN),
                btn -> confirmBet()
        ).dimensions(guiX + 195, guiY + 215, 70, 18).build());

        // Bet type buttons - store positions for highlighting (moved up 10px)
        int btnY = guiY + 165;
        int btnH = 20;

        // First row: TAI, XIU, ODD, EVEN
        taiBtnX = guiX + 10; taiBtnY = btnY; taiBtnW = 50; taiBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TAI").formatted(Formatting.GREEN),
                btn -> selectBetType("TAI")
        ).dimensions(taiBtnX, taiBtnY, taiBtnW, taiBtnH).build());

        xiuBtnX = guiX + 65; xiuBtnY = btnY; xiuBtnW = 50; xiuBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("XIU").formatted(Formatting.RED),
                btn -> selectBetType("XIU")
        ).dimensions(xiuBtnX, xiuBtnY, xiuBtnW, xiuBtnH).build());

        oddBtnX = guiX + 120; oddBtnY = btnY; oddBtnW = 40; oddBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("ODD").formatted(Formatting.YELLOW),
                btn -> selectBetType("ODD")
        ).dimensions(oddBtnX, oddBtnY, oddBtnW, oddBtnH).build());

        evenBtnX = guiX + 165; evenBtnY = btnY; evenBtnW = 40; evenBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("EVEN").formatted(Formatting.YELLOW),
                btn -> selectBetType("EVEN")
        ).dimensions(evenBtnX, evenBtnY, evenBtnW, evenBtnH).build());

        // Second row: PAIR, TRIPLE, Numbers 1-6
        int row2Y = btnY + 23;

        pairBtnX = guiX + 10; pairBtnY = row2Y; pairBtnW = 45; pairBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("PAIR").formatted(Formatting.LIGHT_PURPLE),
                btn -> selectBetType("PAIR")
        ).dimensions(pairBtnX, pairBtnY, pairBtnW, pairBtnH).build());

        tripleBtnX = guiX + 60; tripleBtnY = row2Y; tripleBtnW = 55; tripleBtnH = btnH;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("TRIPLE").formatted(Formatting.GOLD),
                btn -> selectBetType("TRIPLE")
        ).dimensions(tripleBtnX, tripleBtnY, tripleBtnW, tripleBtnH).build());

        // Number buttons (1-6)
        for (int i = 1; i <= 6; i++) {
            final int num = i;
            int bx = guiX + 120 + (i-1) * 28;
            numBtnX[i-1] = bx; numBtnY[i-1] = row2Y; numBtnW[i-1] = 25; numBtnH[i-1] = btnH;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.valueOf(num)).formatted(Formatting.AQUA),
                    btn -> selectBetType(String.valueOf(num))
            ).dimensions(bx, row2Y, 25, btnH).build());
        }
    }

    private void selectBetType(String type) {
        selectedBetType = type;
        if (type.matches("[1-6]")) {
            selectedNumber = Integer.parseInt(type);
        } else {
            selectedNumber = -1;
        }
    }

    private void confirmBet() {
        if (betAmount <= 0 || selectedBetType == null || gameState != TaiXiuGameManager.GameState.BETTING) return;

        if (selectedBetType.matches("[1-6]")) {
            sendAction("NUMBER", betAmount, selectedNumber);
        } else {
            sendAction(selectedBetType, betAmount, 0);
        }
        // Don't clear selection - allow multiple bets
    }

    private void sendAction(String action, int amount, int number) {
        com.pokermc.taixiu.network.TaiXiuNetworking.TaiXiuActionPayload payload =
                new com.pokermc.taixiu.network.TaiXiuNetworking.TaiXiuActionPayload(pos, action, amount, number);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No local countdown - use server values directly
        // Timer updates come from server every second

        context.fill(0, 0, width, height, 0xA0000000);

        // Main background
        context.fill(guiX, guiY, guiX + GUI_W, guiY + GUI_H, BG_DARK);
        drawBorder(context, guiX, guiY, GUI_W, GUI_H, BORDER_GOLD, 2);

        // Balance and Timer row
        int infoY = guiY + 28;
        context.drawText(textRenderer, "Bal: " + playerBalance, guiX + 10, infoY, COLOR_TEXT, false);

        // Timer
        String timerText;
        int timerColor;
        if (gameState == TaiXiuGameManager.GameState.BETTING) {
            timerText = "Rolling in: " + secondsUntilRoll + "s";
            timerColor = secondsUntilRoll <= 3 ? COLOR_RED : COLOR_AQUA;
        } else if (gameState == TaiXiuGameManager.GameState.ROLLING) {
            timerText = "ROLLING...";
            timerColor = COLOR_GREEN;
        } else {
            timerText = "Next: " + secondsUntilNextBet + "s";
            timerColor = COLOR_TEXT_DIM;
        }
        int tw = textRenderer.getWidth(timerText);
        context.drawText(textRenderer, timerText, guiX + GUI_W - tw - 10, infoY, timerColor, false);

        // Player bets display
        if (!playerBets.isEmpty() || !playerBetString.isEmpty()) {
            String betsText = "Bets: " + (playerBetString.isEmpty() ? "" : playerBetString);
            // Truncate if too long
            if (textRenderer.getWidth(betsText) > GUI_W - 20) {
                betsText = betsText.substring(0, Math.min(30, betsText.length())) + "...";
            }
            context.drawText(textRenderer, betsText, guiX + 10, infoY + 14, COLOR_TEXT_DIM, false);
        }

        // Dice display area
        int diceAreaX = guiX + GUI_W / 2 - 45;
        int diceAreaY = guiY + 55;
        int diceSize = 28;

        // Draw dice
        if (gameState == TaiXiuGameManager.GameState.RESULT) {
            drawDie(context, diceAreaX, diceAreaY, diceSize, lastDie1);
            drawDie(context, diceAreaX + diceSize + 6, diceAreaY, diceSize, lastDie2);
            drawDie(context, diceAreaX + (diceSize + 6) * 2, diceAreaY, diceSize, lastDie3);

            String totalText = "TOTAL: " + lastTotal;
            int totalColor = lastIsTriple ? COLOR_GREEN : (lastTotal >= 11 ? COLOR_GREEN : COLOR_RED);
            int totalW = textRenderer.getWidth(totalText);
            context.drawText(textRenderer, totalText, guiX + GUI_W / 2 - totalW / 2, diceAreaY + diceSize + 8, totalColor, false);

            String resultStr = lastResultString;
            int rw = textRenderer.getWidth(resultStr);
            context.drawText(textRenderer, resultStr, guiX + GUI_W / 2 - rw / 2, diceAreaY + diceSize + 18, COLOR_TEXT, false);
        } else if (gameState == TaiXiuGameManager.GameState.ROLLING) {
            drawDie(context, diceAreaX, diceAreaY, diceSize, die1);
            drawDie(context, diceAreaX + diceSize + 6, diceAreaY, diceSize, die2);
            drawDie(context, diceAreaX + (diceSize + 6) * 2, diceAreaY, diceSize, die3);
        } else {
            // Static dice during betting
            drawDie(context, diceAreaX, diceAreaY, diceSize, 1);
            drawDie(context, diceAreaX + diceSize + 6, diceAreaY, diceSize, 1);
            drawDie(context, diceAreaX + (diceSize + 6) * 2, diceAreaY, diceSize, 1);
        }

        // Bet type label (moved up 20px total to avoid overlap)
        context.drawText(textRenderer, "Bet Type:", guiX + 10, guiY + 143, COLOR_TEXT_DIM, false);

        // Bet amount label (aligned with Bet Type)
        context.drawText(textRenderer, "Bet Amount:", guiX + 10, guiY + 218, COLOR_TEXT, false);

        // Highlight selected bet type with orange border
        if (selectedBetType != null) {
            if (selectedBetType.equals("TAI")) {
                drawHighlightBorder(context, taiBtnX-1, taiBtnY-1, taiBtnW+2, taiBtnH+2);
            } else if (selectedBetType.equals("XIU")) {
                drawHighlightBorder(context, xiuBtnX-1, xiuBtnY-1, xiuBtnW+2, xiuBtnH+2);
            } else if (selectedBetType.equals("ODD")) {
                drawHighlightBorder(context, oddBtnX-1, oddBtnY-1, oddBtnW+2, oddBtnH+2);
            } else if (selectedBetType.equals("EVEN")) {
                drawHighlightBorder(context, evenBtnX-1, evenBtnY-1, evenBtnW+2, evenBtnH+2);
            } else if (selectedBetType.equals("PAIR")) {
                drawHighlightBorder(context, pairBtnX-1, pairBtnY-1, pairBtnW+2, pairBtnH+2);
            } else if (selectedBetType.equals("TRIPLE")) {
                drawHighlightBorder(context, tripleBtnX-1, tripleBtnY-1, tripleBtnW+2, tripleBtnH+2);
            } else if (selectedBetType.matches("[1-6]")) {
                int idx = Integer.parseInt(selectedBetType) - 1;
                drawHighlightBorder(context, numBtnX[idx]-1, numBtnY[idx]-1, numBtnW[idx]+2, numBtnH[idx]+2);
            }
        }

        // Show selected bet info next to Confirm button
        if (selectedBetType != null && betAmount > 0) {
            String betInfo = "Bet " + betAmount + " ZC for " + selectedBetType;
            context.drawText(textRenderer, betInfo, guiX + 10, guiY + 236, COLOR_ORANGE, false);
        }

        // Draw tooltip if hovering over help button
        if (mouseX >= helpBtnX && mouseX <= helpBtnX + helpBtnW &&
            mouseY >= helpBtnY && mouseY <= helpBtnY + helpBtnH) {
            drawPayoutTooltip(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawHighlightBorder(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + 2, COLOR_ORANGE);
        ctx.fill(x, y + h - 2, x + w, y + h, COLOR_ORANGE);
        ctx.fill(x, y, x + 2, y + h, COLOR_ORANGE);
        ctx.fill(x + w - 2, y, x + w, y + h, COLOR_ORANGE);
    }

    private void drawDie(DrawContext context, int x, int y, int size, int value) {
        context.fill(x, y, x + size, y + size, 0xFFFFFFFF);

        int dotSize = 2;
        int centerOffset = size / 2;
        int quarterOffset = size / 4;
        int dotColor = 0xFF000000;

        if (value == 1 || value == 3 || value == 5) {
            context.fill(x + centerOffset - dotSize/2, y + centerOffset - dotSize/2,
                        x + centerOffset + dotSize/2, y + centerOffset + dotSize/2, dotColor);
        }
        if (value >= 2) {
            context.fill(x + quarterOffset - dotSize/2, y + quarterOffset - dotSize/2,
                        x + quarterOffset + dotSize/2, y + quarterOffset + dotSize/2, dotColor);
            context.fill(x + size - quarterOffset - dotSize/2, y + size - quarterOffset - dotSize/2,
                        x + size - quarterOffset + dotSize/2, y + size - quarterOffset + dotSize/2, dotColor);
        }
        if (value >= 4) {
            context.fill(x + size - quarterOffset - dotSize/2, y + quarterOffset - dotSize/2,
                        x + size - quarterOffset + dotSize/2, y + quarterOffset + dotSize/2, dotColor);
            context.fill(x + quarterOffset - dotSize/2, y + size - quarterOffset - dotSize/2,
                        x + quarterOffset + dotSize/2, y + size - quarterOffset + dotSize/2, dotColor);
        }
        if (value == 6) {
            context.fill(x + quarterOffset - dotSize/2, y + centerOffset - dotSize/2,
                        x + quarterOffset + dotSize/2, y + centerOffset + dotSize/2, dotColor);
            context.fill(x + size - quarterOffset - dotSize/2, y + centerOffset - dotSize/2,
                        x + size - quarterOffset + dotSize/2, y + centerOffset + dotSize/2, dotColor);
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        ctx.fill(x, y, x + w, y + thickness, color);
        ctx.fill(x, y + h - thickness, x + w, y + h, color);
        ctx.fill(x, y, x + thickness, y + h, color);
        ctx.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private void drawPayoutTooltip(DrawContext context, int mouseX, int mouseY) {
        String[] payouts = {
            "TAI/XIU: 1:1",
            "ODD/EVEN: 1:1",
            "PAIR: 8:1",
            "TRIPLE: 150:1",
            "NUMBER (1-6): 1:1 per die"
        };

        int tooltipW = 150;
        int tooltipH = payouts.length * 12 + 10;
        int tooltipX = mouseX - tooltipW - 5;
        int tooltipY = mouseY + 10;

        // Adjust if off screen
        if (tooltipX < 0) tooltipX = mouseX + 15;
        if (tooltipY + tooltipH > height) tooltipY = mouseY - tooltipH - 5;

        // Tooltip background
        context.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xD0000000);
        drawBorder(context, tooltipX, tooltipY, tooltipW, tooltipH, BORDER_GOLD, 1);

        // Payout text
        int y = tooltipY + 6;
        for (String payout : payouts) {
            context.drawText(textRenderer, payout, tooltipX + 8, y, COLOR_TEXT, false);
            y += 12;
        }
    }

    public void updateState(String json) {
        try {
            stateJson = new Gson().fromJson(json, JsonObject.class);

            // Debug: Log updates
            String newState = stateJson.has("state") ? stateJson.get("state").getAsString() : "?";
            long time = stateJson.has("currentTime") ? stateJson.get("currentTime").getAsLong() : 0;
            // Log every second (20 ticks) to reduce spam
            if (time % 20 == 0) {
                System.out.println("[Tai Xiu Screen] updateState: " + newState + " at time " + time);
            }

            if (stateJson.has("playerBalance")) {
                playerBalance = stateJson.get("playerBalance").getAsInt();
            }
            if (stateJson.has("playerBetString")) {
                playerBetString = stateJson.get("playerBetString").getAsString();
            }
            if (stateJson.has("playerBets")) {
                JsonArray betsArray = stateJson.getAsJsonArray("playerBets");
                playerBets.clear();
                for (JsonElement e : betsArray) {
                    JsonObject b = e.getAsJsonObject();
                    playerBets.add(b.get("display").getAsString());
                }
            }

            if (stateJson.has("state")) {
                gameState = TaiXiuGameManager.GameState.valueOf(stateJson.get("state").getAsString());
            }

            if (stateJson.has("die1")) die1 = stateJson.get("die1").getAsInt();
            if (stateJson.has("die2")) die2 = stateJson.get("die2").getAsInt();
            if (stateJson.has("die3")) die3 = stateJson.get("die3").getAsInt();

            // Timer values from server - updated every second
            if (stateJson.has("secondsUntilRoll")) {
                secondsUntilRoll = stateJson.get("secondsUntilRoll").getAsInt();
            }
            if (stateJson.has("secondsUntilNextBet")) {
                secondsUntilNextBet = stateJson.get("secondsUntilNextBet").getAsInt();
            }

            if (stateJson.has("lastResult")) {
                JsonObject result = stateJson.getAsJsonObject("lastResult");
                lastDie1 = result.get("die1").getAsInt();
                lastDie2 = result.get("die2").getAsInt();
                lastDie3 = result.get("die3").getAsInt();
                lastTotal = result.get("total").getAsInt();
                lastIsTriple = result.get("isTriple").getAsBoolean();
                lastResultString = result.get("resultString").getAsString();
            }

            if (stateJson.has("history")) {
                JsonArray histArray = stateJson.getAsJsonArray("history");
                history.clear();
                for (JsonElement e : histArray) {
                    JsonObject r = e.getAsJsonObject();
                    history.add(new DiceResult(
                            r.get("die1").getAsInt(),
                            r.get("die2").getAsInt(),
                            r.get("die3").getAsInt(),
                            r.get("total").getAsInt(),
                            r.get("isTriple").getAsBoolean(),
                            r.get("resultString").getAsString()
                    ));
                }
            }

            if (stateJson.has("playerBetHistory")) {
                JsonArray betHistArray = stateJson.getAsJsonArray("playerBetHistory");
                playerBetHistory.clear();
                for (JsonElement e : betHistArray) {
                    JsonObject r = e.getAsJsonObject();
                    playerBetHistory.add(new PlayerBetResultItem(
                            r.get("betDisplay").getAsString(),
                            r.get("amount").getAsInt(),
                            r.get("won").getAsBoolean(),
                            r.get("winAmount").getAsInt()
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[Tai Xiu] Error updating state: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        super.close();
    }

    private static class DiceResult {
        final int d1, d2, d3, total;
        final boolean isTriple;
        final String resultString;

        DiceResult(int d1, int d2, int d3, int total, boolean isTriple, String resultString) {
            this.d1 = d1;
            this.d2 = d2;
            this.d3 = d3;
            this.total = total;
            this.isTriple = isTriple;
            this.resultString = resultString;
        }
    }

    private static class PlayerBetResultItem {
        final String betDisplay;
        final int amount;
        final boolean won;
        final int winAmount;

        PlayerBetResultItem(String betDisplay, int amount, boolean won, int winAmount) {
            this.betDisplay = betDisplay;
            this.amount = amount;
            this.won = won;
            this.winAmount = winAmount;
        }
    }
}
