package com.pokermc.taixiu.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokermc.PokerMod;
import com.pokermc.taixiu.blockentity.TaiXiuTableBlockEntity;
import com.pokermc.taixiu.game.TaiXiuGameManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Networking for Tai Xiu (Sic Bo).
 */
public class TaiXiuNetworking {

    public static final Identifier OPEN_TAIXIU_ID =
            Identifier.of(PokerMod.MOD_ID, "open_taixiu");
    public static final Identifier TAIXIU_STATE_ID =
            Identifier.of(PokerMod.MOD_ID, "taixiu_state");
    public static final Identifier TAIXIU_ACTION_ID =
            Identifier.of(PokerMod.MOD_ID, "taixiu_action");

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Payload to open the Tai Xiu screen.
     */
    public record OpenTaiXiuPayload(BlockPos pos, String stateJson)
            implements CustomPayload {

        public static final Id<OpenTaiXiuPayload> ID = new Id<>(OPEN_TAIXIU_ID);
        public static final PacketCodec<PacketByteBuf, OpenTaiXiuPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenTaiXiuPayload::pos,
                PacketCodecs.STRING, OpenTaiXiuPayload::stateJson,
                OpenTaiXiuPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload to update Tai Xiu state on client.
     */
    public record TaiXiuStatePayload(BlockPos pos, String stateJson)
            implements CustomPayload {

        public static final Id<TaiXiuStatePayload> ID = new Id<>(TAIXIU_STATE_ID);
        public static final PacketCodec<PacketByteBuf, TaiXiuStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, TaiXiuStatePayload::pos,
                PacketCodecs.STRING, TaiXiuStatePayload::stateJson,
                TaiXiuStatePayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload for player actions (BET on Tai/Xiu/Odd/Even/Pair/Triple/Number).
     */
    public record TaiXiuActionPayload(BlockPos pos, String action, int amount, int number)
            implements CustomPayload {

        public static final Id<TaiXiuActionPayload> ID = new Id<>(TAIXIU_ACTION_ID);
        public static final PacketCodec<PacketByteBuf, TaiXiuActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, TaiXiuActionPayload::pos,
                PacketCodecs.STRING, TaiXiuActionPayload::action,
                PacketCodecs.VAR_INT, TaiXiuActionPayload::amount,
                PacketCodecs.VAR_INT, TaiXiuActionPayload::number,
                TaiXiuActionPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Serialize the Tai Xiu game state to JSON.
     */
    public static String serializeState(TaiXiuGameManager game, ServerPlayerEntity viewer, long worldTime) {
        JsonObject root = new JsonObject();

        // Game state
        root.addProperty("state", game.getState().name());
        root.addProperty("currentTime", worldTime);
        root.addProperty("secondsUntilRoll", game.getSecondsUntilRoll());
        root.addProperty("secondsUntilNextBet", game.getSecondsUntilNextBet());

        // Player balance
        root.addProperty("playerBalance", com.pokermc.common.config.ZCoinStorage.getBalance(viewer));
        root.addProperty("playerBetTotal", game.getPlayerBetTotal(viewer.getUuid()));
        root.addProperty("playerBetString", game.getPlayerBetString(viewer.getUuid()));

        // Current dice (animated)
        root.addProperty("die1", game.getAnimDie1());
        root.addProperty("die2", game.getAnimDie2());
        root.addProperty("die3", game.getAnimDie3());

        // Last result
        TaiXiuGameManager.DiceResult lastResult = game.getLastResult();
        if (lastResult != null) {
            JsonObject result = new JsonObject();
            result.addProperty("die1", lastResult.die1);
            result.addProperty("die2", lastResult.die2);
            result.addProperty("die3", lastResult.die3);
            result.addProperty("total", lastResult.total);
            result.addProperty("isTriple", lastResult.isTriple);
            result.addProperty("resultString", lastResult.getResultString());
            root.add("lastResult", result);
        }

        // History (last 20 results for client)
        JsonArray historyArray = new JsonArray();
        int start = Math.max(0, game.getHistory().size() - 20);
        for (int i = start; i < game.getHistory().size(); i++) {
            TaiXiuGameManager.DiceResult result = game.getHistory().get(i);
            JsonObject r = new JsonObject();
            r.addProperty("die1", result.die1);
            r.addProperty("die2", result.die2);
            r.addProperty("die3", result.die3);
            r.addProperty("total", result.total);
            r.addProperty("isTriple", result.isTriple);
            r.addProperty("resultString", result.getResultString());
            historyArray.add(r);
        }
        root.add("history", historyArray);

        // Player's active bets (for display)
        JsonArray betsArray = new JsonArray();
        for (TaiXiuGameManager.Bet bet : game.getPlayerBets(viewer.getUuid())) {
            JsonObject b = new JsonObject();
            b.addProperty("display", bet.betDisplay);
            b.addProperty("amount", bet.amount);
            betsArray.add(b);
        }
        root.add("playerBets", betsArray);

        // Player's bet history (win/loss records) - last 10
        JsonArray betHistoryArray = new JsonArray();
        for (TaiXiuGameManager.PlayerBetResult result : game.getPlayerBetHistory(viewer.getUuid())) {
            JsonObject r = new JsonObject();
            r.addProperty("betDisplay", result.betDisplay);
            r.addProperty("amount", result.amount);
            r.addProperty("won", result.won);
            r.addProperty("winAmount", result.winAmount);
            betHistoryArray.add(r);
        }
        root.add("playerBetHistory", betHistoryArray);

        return GSON.toJson(root);
    }

    /**
     * Handle player action from client.
     */
    public static void handleAction(ServerPlayerEntity player, TaiXiuActionPayload payload) {
        if (!(player.getEntityWorld().getBlockEntity(payload.pos()) instanceof TaiXiuTableBlockEntity be)) {
            return;
        }

        TaiXiuGameManager game = be.getGameManager();
        String result;

        try {
            result = switch (payload.action()) {
                case "TAI" -> game.placeBet(player, TaiXiuGameManager.BetType.TAI, payload.amount());
                case "XIU" -> game.placeBet(player, TaiXiuGameManager.BetType.XIU, payload.amount());
                case "ODD" -> game.placeBet(player, TaiXiuGameManager.BetType.ODD, payload.amount());
                case "EVEN" -> game.placeBet(player, TaiXiuGameManager.BetType.EVEN, payload.amount());
                case "PAIR" -> game.placeBet(player, TaiXiuGameManager.BetType.PAIR, payload.amount());
                case "TRIPLE" -> game.placeBet(player, TaiXiuGameManager.BetType.TRIPLE, payload.amount());
                case "NUMBER" -> game.placeNumberBet(player, payload.number(), payload.amount());
                default -> "Unknown action: " + payload.action();
            };
        } catch (Exception e) {
            result = "Error: " + e.getMessage();
        }

        player.sendMessage(net.minecraft.text.Text.literal("§6[Tai Xiu] §f" + result), false);

        // Send updated state
        String json = serializeState(game, player, player.getEntityWorld().getTime());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player, new TaiXiuStatePayload(be.getPos(), json));
    }
}
