package com.pokermc.poker.network;

import com.google.gson.*;
import com.pokermc.common.config.PokerConfig;
import com.pokermc.common.config.TradeConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.common.game.Card;
import com.pokermc.poker.blockentity.PokerTableBlockEntity;
import com.pokermc.poker.game.PokerGame;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PokerNetworking {

    private static final Gson GSON = new Gson();

    // ── Payloads ───────────────────────────────────────────────────────────────

    public record OpenTablePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenTablePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_table"));
        public static final PacketCodec<PacketByteBuf, OpenTablePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenTablePayload::pos,
                PacketCodecs.STRING, OpenTablePayload::stateJson,
                OpenTablePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GameStatePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<GameStatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "game_state"));
        public static final PacketCodec<PacketByteBuf, GameStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, GameStatePayload::pos,
                PacketCodecs.STRING, GameStatePayload::stateJson,
                GameStatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * C2S player action.
     * action  = "JOIN" / "LEAVE" / "FOLD" / "CHECK" / "CALL" / "RAISE" /
     *           "ALLIN" / "START" / "RESET" / "CREATE" /
     *           "DEPOSIT" / "WITHDRAW"
     * amount  = numeric argument (raise amount, item count, bet level, …)
     * data    = extra string payload (item ID for DEPOSIT/WITHDRAW, "" otherwise)
     */
    public record PlayerActionPayload(BlockPos pos, String action, int amount, String data)
            implements CustomPayload {
        public static final CustomPayload.Id<PlayerActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "player_action"));
        public static final PacketCodec<PacketByteBuf, PlayerActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC,  PlayerActionPayload::pos,
                PacketCodecs.STRING,    PlayerActionPayload::action,
                PacketCodecs.VAR_INT,   PlayerActionPayload::amount,
                PacketCodecs.STRING,    PlayerActionPayload::data,
                PlayerActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── State serialization ────────────────────────────────────────────────────

    public static String serializeState(PokerGame game, ServerPlayerEntity viewer, long worldTime) {
        String viewerName = viewer.getName().getString();
        JsonObject root = new JsonObject();
        root.addProperty("phase",           game.getPhase().name());
        root.addProperty("pot",             game.getPot());
        root.addProperty("currentBet",      game.getCurrentBet());
        root.addProperty("currentPlayer",   game.getCurrentPlayerName());
        root.addProperty("lastWinner",      game.getLastWinner());
        root.addProperty("lastWinningHand", game.getLastWinningHand());
        root.addProperty("lastPotWon",      game.getLastPotWon());
        root.addProperty("status",          game.getStatusMessage());
        root.addProperty("betLevel",        game.getBetLevel());
        root.addProperty("dealerIndex",     game.getDealerIndex());
        root.addProperty("isEmpty",         game.getPlayers().isEmpty()
                                         && game.getPendingPlayers().isEmpty());
        root.addProperty("minZcToJoin",     PokerConfig.get().minZcToJoin);

        // Community cards
        JsonArray community = new JsonArray();
        for (Card c : game.getCommunityCards()) community.add(c.toCode());
        root.add("community", community);

        // Players
        JsonArray playersArr = new JsonArray();
        for (PokerGame.PlayerState p : game.getPlayers()) {
            JsonObject po = new JsonObject();
            po.addProperty("name",       p.name);
            po.addProperty("chips",      p.chips);
            po.addProperty("currentBet", p.currentBet);
            po.addProperty("folded",     p.folded);
            po.addProperty("allIn",      p.allIn);
            boolean reveal = p.name.equals(viewerName) || game.getPhase() == PokerGame.Phase.SHOWDOWN;
            JsonArray holeArr = new JsonArray();
            if (reveal) for (Card c : p.holeCards) holeArr.add(c.toCode());
            else        for (int i = 0; i < p.holeCards.size(); i++) holeArr.add("??");
            po.add("holeCards", holeArr);
            playersArr.add(po);
        }
        root.add("players", playersArr);
        root.addProperty("owner", game.getPlayers().isEmpty() ? "" : game.getPlayers().get(0).name);

        // Pending players
        JsonArray pendingArr = new JsonArray();
        for (String n : game.getPendingPlayers()) pendingArr.add(n);
        root.add("pendingPlayers", pendingArr);

        // Per-viewer ZCoin balance (from inventory)
        root.addProperty("bankBalance", ZCoinStorage.getBalance(viewer));

        // Trade items: only iron, gold, emerald, diamond
        var trades = TradeConfig.get();
        JsonArray tradeArr = new JsonArray();
        String[] allowed = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond"};
        for (String id : allowed) {
            if (!trades.buyRates.containsKey(id)) continue;
            JsonObject to = new JsonObject();
            to.addProperty("id", id);
            to.addProperty("buyRate", trades.buyRates.get(id));
            to.addProperty("sellRate", trades.sellRates.getOrDefault(id, trades.buyRates.get(id)));
            to.addProperty("sellGives", trades.sellGives.getOrDefault(id, 1));
            tradeArr.add(to);
        }
        root.add("tradeItems", tradeArr);

        int turnSec = PokerConfig.get().turnTimeSeconds;
        if (turnSec > 0 && game.getPhase() != PokerGame.Phase.WAITING && game.getPhase() != PokerGame.Phase.SHOWDOWN
                && game.getCurrentPlayerName().equals(viewerName)) {
            long elapsed = Math.max(0, worldTime - game.getTurnStartTick());
            int remaining = (int) Math.max(0, turnSec - elapsed / 20);
            root.addProperty("turnTimeRemaining", remaining);
        } else {
            root.addProperty("turnTimeRemaining", 0);
        }

        return GSON.toJson(root);
    }

    /** Broadcast personalized state to every viewer. */
    public static void broadcastState(PokerTableBlockEntity be) {
        long worldTime = be.getWorld() != null ? be.getWorld().getTime() : 0;
        for (ServerPlayerEntity sp : be.getViewers()) {
            String json = serializeState(be.getGame(), sp, worldTime);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    sp, new GameStatePayload(be.getPos(), json));
        }
    }

    // ── Action dispatcher ─────────────────────────────────────────────────────

    public static void handlePlayerAction(ServerPlayerEntity player, PlayerActionPayload payload) {
        BlockPos pos = payload.pos();
        if (!(player.getEntityWorld().getBlockEntity(pos) instanceof PokerTableBlockEntity be)) return;

        String name   = player.getName().getString();
        String action = payload.action();
        int    amount = payload.amount();
        String data   = payload.data() != null ? payload.data() : "";

        boolean changed = switch (action) {
            case "CREATE"   -> handleCreate(be, player);
            case "JOIN"     -> handleJoin(be, player);
            case "LEAVE"    -> handleLeave(be, player);
            case "START"    -> handleStart(be, player);
            case "RESET"    -> handleReset(be, player);
            case "DEPOSIT"  -> handleDeposit(be, player, amount, data);
            case "WITHDRAW" -> handleWithdraw(be, player, amount, data);
            case "FOLD"     -> be.getGame().performAction(name, PokerGame.Action.FOLD,  0);
            case "CHECK"    -> be.getGame().performAction(name, PokerGame.Action.CHECK, 0);
            case "CALL"     -> be.getGame().performAction(name, PokerGame.Action.CALL,  0);
            case "RAISE"    -> be.getGame().performAction(name, PokerGame.Action.RAISE, amount);
            case "ALLIN"    -> be.getGame().performAction(name, PokerGame.Action.ALLIN, 0);
            default -> false;
        };

        if (changed) {
            be.markDirty();
            broadcastState(be);
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    /** First person creates room and enters as host. Uses config blinds. */
    private static boolean handleCreate(PokerTableBlockEntity be, ServerPlayerEntity player) {
        if (!be.getGame().getPlayers().isEmpty()) return false;
        int minReq = PokerConfig.get().minZcToJoin;
        int balance = ZCoinStorage.getBalance(player);
        if (balance < minReq) {
            be.getGame().setStatusMessage("Need at least " + minReq + " ZC to create room.");
            broadcastState(be);
            return false;
        }
        String name = player.getName().getString();
        int chips = resolveStartChips(player);
        if (chips <= 0) {
            be.getGame().setStatusMessage("Need ZCoin to create room. Use Exchange to deposit items.");
            broadcastState(be);
            return false;
        }
        be.addViewer(player);
        return be.getGame().addPlayer(name, chips);
    }

    private static boolean handleJoin(PokerTableBlockEntity be, ServerPlayerEntity player) {
        String name = player.getName().getString();
        if (be.getGame().hasPlayer(name)) {
            be.addViewer(player);
            return true;
        }
        if (be.getGame().getPlayers().isEmpty()) return false; // Use CREATE for first player
        int minReq = PokerConfig.get().minZcToJoin;
        int balance = ZCoinStorage.getBalance(player);
        if (balance < minReq) {
            be.getGame().setStatusMessage("Need at least " + minReq + " ZC to join.");
            broadcastState(be);
            return false;
        }
        // When joining as PENDING (game in progress): don't take chips yet - they stay in inventory
        // until resetToWaiting runs. Taking here would lose them (addPlayer doesn't store for pending).
        if (be.getGame().getPhase() != PokerGame.Phase.WAITING) {
            boolean changed = be.getGame().addPlayer(name, 0);
            if (changed) be.addViewer(player);
            return changed;
        }
        int chips = resolveStartChips(player);
        if (chips <= 0) {
            be.getGame().setStatusMessage("Need ZCoin to join. Use Exchange to deposit items.");
            broadcastState(be);
            return false;
        }
        boolean changed = be.getGame().addPlayer(name, chips);
        if (changed) be.addViewer(player);
        return changed;
    }

    /** Take ZCoin from inventory for table. Never creates chips from nothing. */
    private static int resolveStartChips(ServerPlayerEntity player) {
        return ZCoinStorage.takeAll(player);
    }

    private static boolean handleLeave(PokerTableBlockEntity be, ServerPlayerEntity player) {
        String name = player.getName().getString();
        be.getGame().getPlayers().stream()
                .filter(p -> p.name.equals(name))
                .findFirst()
                .ifPresent(ps -> {
                    if (ps.chips > 0) ZCoinStorage.giveBack(player, ps.chips);
                });
        be.removeViewer(player);
        boolean changed = be.getGame().removePlayer(name);
        if (changed && be.getGame().getPlayers().isEmpty() && be.getGame().getPendingPlayers().isEmpty()) {
            be.getGame().resetWhenEmpty();
        }
        return changed;
    }

    private static boolean handleStart(PokerTableBlockEntity be, ServerPlayerEntity player) {
        var server = be.getWorld() != null ? be.getWorld().getServer() : null;
        for (String name : be.getGame().kickPlayersBelowMin(server)) {
            be.removeViewerByName(name);
        }
        return be.getGame().startGame();
    }

    private static boolean handleReset(PokerTableBlockEntity be, ServerPlayerEntity player) {
        String owner = be.getGame().getPlayers().isEmpty() ? "" : be.getGame().getPlayers().get(0).name;
        if (!player.getName().getString().equals(owner)) return false;
        var server = be.getWorld() != null ? be.getWorld().getServer() : null;
        be.getGame().resetToWaiting(server);
        for (String name : be.getGame().kickPlayersBelowMin(server)) {
            be.removeViewerByName(name);
        }
        be.getGame().startGame();
        return true;
    }

    private static final String[] TRADE_ALLOWED = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond"};

    /** Buy ZC with items: amount = item count, data = itemId. Only iron, gold, emerald, diamond. */
    private static boolean handleDeposit(PokerTableBlockEntity be, ServerPlayerEntity player, int amount, String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (!java.util.Arrays.asList(TRADE_ALLOWED).contains(itemId)) return false;
        var trades = TradeConfig.get();
        int rate = trades.buyRates.getOrDefault(itemId, 0);
        if (rate <= 0) return false;

        Identifier itemIdParsed = Identifier.tryParse(itemId);
        if (itemIdParsed == null || !Registries.ITEM.containsId(itemIdParsed)) return false;
        Item targetItem = Registries.ITEM.get(itemIdParsed);

        // Count available
        int available = countItems(player, targetItem);
        if (available <= 0) return false;

        int toUse = (amount > 0) ? Math.min(amount, available) : available;
        removeItems(player, targetItem, toUse);

        int gained = toUse * rate;
        ZCoinStorage.add(player, gained);
        be.getGame().setStatusMessage(player.getName().getString() + " deposited +" + gained + " ZC.");
        return true;
    }

    /** Sell ZC for items: amount = number of withdraw units, data = itemId. Only iron, gold, emerald, diamond. */
    private static boolean handleWithdraw(PokerTableBlockEntity be, ServerPlayerEntity player, int amount, String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (!java.util.Arrays.asList(TRADE_ALLOWED).contains(itemId)) return false;
        var trades = TradeConfig.get();
        int rate = trades.sellRates.getOrDefault(itemId, 0);
        int gives = trades.sellGives.getOrDefault(itemId, rate);
        if (rate <= 0) return false;

        Identifier itemIdParsed = Identifier.tryParse(itemId);
        if (itemIdParsed == null || !Registries.ITEM.containsId(itemIdParsed)) return false;
        Item targetItem = Registries.ITEM.get(itemIdParsed);

        int units = Math.max(1, amount);
        int totalCost = units * rate;
        int toGive = units * gives;
        String name = player.getName().getString();

        if (!ZCoinStorage.deduct(player, totalCost)) return false;

        // Give items to player
        int remaining = toGive;
        while (remaining > 0) {
            int stack = Math.min(remaining, 64);
            ItemStack give = new ItemStack(targetItem, stack);
            if (!player.getInventory().insertStack(give)) {
                player.dropItem(give, false);
            }
            remaining -= stack;
        }
        be.getGame().setStatusMessage(name + " withdrew " + toGive + "× " + itemId.split(":")[1] + ".");
        return true;
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private static int countItems(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        ItemStack offhand = player.getOffHandStack();
        if (offhand.getItem() == item) count += offhand.getCount();
        return count;
    }

    private static void removeItems(ServerPlayerEntity player, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item && remaining > 0) {
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
            }
        }
        if (remaining > 0) {
            ItemStack offhand = player.getOffHandStack();
            if (offhand.getItem() == item) {
                int take = Math.min(offhand.getCount(), remaining);
                offhand.decrement(take);
            }
        }
    }
}
