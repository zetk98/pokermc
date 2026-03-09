package com.pokermc.blackjack.network;

import com.google.gson.*;
import com.pokermc.blackjack.blockentity.BlackjackTableBlockEntity;
import com.pokermc.blackjack.game.BlackjackGame;
import com.pokermc.common.config.TradeConfig;
import com.pokermc.common.config.ZCoinStorage;
import com.pokermc.common.game.Card;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class BlackjackNetworking {

    private static final Gson GSON = new Gson();

    public record OpenBlackjackPayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<OpenBlackjackPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "open_blackjack"));
        public static final PacketCodec<PacketByteBuf, OpenBlackjackPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenBlackjackPayload::pos,
                PacketCodecs.STRING, OpenBlackjackPayload::stateJson,
                OpenBlackjackPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BlackjackStatePayload(BlockPos pos, String stateJson) implements CustomPayload {
        public static final CustomPayload.Id<BlackjackStatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "blackjack_state"));
        public static final PacketCodec<PacketByteBuf, BlackjackStatePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, BlackjackStatePayload::pos,
                PacketCodecs.STRING, BlackjackStatePayload::stateJson,
                BlackjackStatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BlackjackActionPayload(BlockPos pos, String action, int amount, String data)
            implements CustomPayload {
        public static final CustomPayload.Id<BlackjackActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of("casinocraft", "blackjack_action"));
        public static final PacketCodec<PacketByteBuf, BlackjackActionPayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, BlackjackActionPayload::pos,
                PacketCodecs.STRING, BlackjackActionPayload::action,
                PacketCodecs.VAR_INT, BlackjackActionPayload::amount,
                PacketCodecs.STRING, BlackjackActionPayload::data,
                BlackjackActionPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static String serializeState(BlackjackGame game, ServerPlayerEntity viewer, long worldTime) {
        String viewerName = viewer.getName().getString();
        boolean isDealer = viewerName.equals(game.getDealerName());
        JsonObject root = new JsonObject();
        root.addProperty("phase", game.getPhase().name());
        root.addProperty("currentPlayer", game.getCurrentPlayerName());
        root.addProperty("status", game.getStatusMessage());
        root.addProperty("maxBet", game.getMaxBet());
        root.addProperty("dealer", game.getDealerName());
        root.addProperty("dealerRevealed", game.isDealerRevealed());
        root.addProperty("betTimeRemaining", game.getBetTimeRemaining());
        root.addProperty("isEmpty", game.getPlayers().isEmpty() && game.getPendingPlayers().isEmpty());
        root.addProperty("minZcToJoin", TradeConfig.get().minZcToJoin);

        JsonArray dealerArr = new JsonArray();
        for (int i = 0; i < game.getDealerHand().size(); i++) {
            if (game.isDealerRevealed() || isDealer)
                dealerArr.add(game.getDealerHand().get(i).toCode());
            else
                dealerArr.add("??");
        }
        root.add("dealerHand", dealerArr);

        JsonArray playersArr = new JsonArray();
        for (BlackjackGame.PlayerState p : game.getPlayers()) {
            JsonObject po = new JsonObject();
            po.addProperty("name", p.name);
            po.addProperty("chips", p.chips);
            po.addProperty("currentBet", p.currentBet);
            po.addProperty("stood", p.stood);
            po.addProperty("busted", p.busted);
            po.addProperty("xilac", p.xilac);
            po.addProperty("xiban", p.xiban);
            po.addProperty("soloDone", p.soloDone);
            if (p.result != null) po.addProperty("result", p.result.name());
            po.addProperty("handValue", p.getHandValue());
            JsonArray handArr = new JsonArray();
            boolean reveal = p.name.equals(viewerName) || game.getPhase() == BlackjackGame.Phase.SETTLEMENT
                    || game.getPhase() == BlackjackGame.Phase.DEALER_SOLO || p.stood || p.busted || p.resolved
                    || (game.getPhase() == BlackjackGame.Phase.DEALER_SOLO && isDealer);
            if (reveal) for (Card c : p.hand) handArr.add(c.toCode());
            else for (int i = 0; i < p.hand.size(); i++) handArr.add("??");
            po.add("hand", handArr);
            playersArr.add(po);
        }
        root.add("players", playersArr);

        JsonArray pendingArr = new JsonArray();
        for (String n : game.getPendingPlayers()) pendingArr.add(n);
        root.add("pendingPlayers", pendingArr);

        root.addProperty("bankBalance", ZCoinStorage.getBalance(viewer));

        var trades = TradeConfig.get();
        JsonArray tradeArr = new JsonArray();
        String[] allowed = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond"};
        for (String id : allowed) {
            if (!trades.buyRates.containsKey(id)) continue;
            Map.Entry<String, Integer> e = Map.entry(id, trades.buyRates.get(id));
            JsonObject to = new JsonObject();
            to.addProperty("id", e.getKey());
            to.addProperty("buyRate", e.getValue());
            to.addProperty("sellRate", trades.sellRates.getOrDefault(e.getKey(), e.getValue()));
            to.addProperty("sellGives", trades.sellGives.getOrDefault(id, 1));
            tradeArr.add(to);
        }
        root.add("tradeItems", tradeArr);

        return GSON.toJson(root);
    }

    public static void broadcastState(BlackjackTableBlockEntity be) {
        long worldTime = be.getWorld() != null ? be.getWorld().getTime() : 0;
        for (ServerPlayerEntity sp : be.getViewers()) {
            String json = serializeState(be.getGame(), sp, worldTime);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    sp, new BlackjackStatePayload(be.getPos(), json));
        }
    }

    public static void handleAction(ServerPlayerEntity player, BlackjackActionPayload payload) {
        BlockPos pos = payload.pos();
        if (!(player.getEntityWorld().getBlockEntity(pos) instanceof BlackjackTableBlockEntity be)) return;

        String name = player.getName().getString();
        String action = payload.action();
        int amount = payload.amount();

        boolean changed = switch (action) {
            case "CREATE" -> handleCreate(be, player);
            case "JOIN" -> handleJoin(be, player);
            case "LEAVE" -> handleLeave(be, player);
            case "START" -> handleStart(be, player);
            case "CONFIRM_BETS" -> be.getGame().confirmBets();
            case "SET_BET" -> { be.getGame().setBet(name, amount); yield true; }
            case "HIT" -> be.getGame().performAction(name, BlackjackGame.Action.HIT);
            case "STAND" -> be.getGame().performAction(name, BlackjackGame.Action.STAND);
            case "DEALER_HIT" -> name.equals(be.getGame().getDealerName()) && be.getGame().dealerHit();
            case "SOLO" -> name.equals(be.getGame().getDealerName()) && be.getGame().dealerSolo(amount);
            case "SOLO_ALL" -> name.equals(be.getGame().getDealerName()) && be.getGame().dealerSoloAll();
            case "DEPOSIT" -> handleDeposit(be, player, amount, payload.data());
            case "WITHDRAW" -> handleWithdraw(be, player, amount, payload.data());
            default -> false;
        };

        if (changed) {
            be.markDirty();
            broadcastState(be);
        }
    }

    private static boolean handleCreate(BlackjackTableBlockEntity be, ServerPlayerEntity player) {
        if (!be.getGame().getPlayers().isEmpty()) return false;
        int minReq = TradeConfig.get().minZcToJoin;
        int balance = ZCoinStorage.getBalance(player);
        if (balance < minReq) {
            be.getGame().setStatusMessage("Need at least " + minReq + " ZC to create room.");
            broadcastState(be);
            return false;
        }
        String name = player.getName().getString();
        int chips = ZCoinStorage.takeAll(player);
        if (chips <= 0) {
            be.getGame().setStatusMessage("Need ZCoin to create room. Use Exchange to deposit items.");
            broadcastState(be);
            return false;
        }
        be.addViewer(player);
        return be.getGame().addPlayer(name, chips);
    }

    private static boolean handleJoin(BlackjackTableBlockEntity be, ServerPlayerEntity player) {
        String name = player.getName().getString();
        if (be.getGame().hasPlayer(name)) {
            be.addViewer(player);
            return true;
        }
        if (be.getGame().getPlayers().isEmpty()) return false;
        int minReq = TradeConfig.get().minZcToJoin;
        int balance = ZCoinStorage.getBalance(player);
        if (balance < minReq) {
            be.getGame().setStatusMessage("Need at least " + minReq + " ZC to join.");
            broadcastState(be);
            return false;
        }
        if (be.getGame().getPhase() != BlackjackGame.Phase.WAITING
                && be.getGame().getPhase() != BlackjackGame.Phase.SETTLEMENT) {
            boolean changed = be.getGame().addPlayer(name, 0);
            if (changed) be.addViewer(player);
            return changed;
        }
        int chips = ZCoinStorage.takeAll(player);
        if (chips <= 0) {
            be.getGame().setStatusMessage("Need ZCoin to join. Use Exchange to deposit items.");
            broadcastState(be);
            return false;
        }
        boolean changed = be.getGame().addPlayer(name, chips);
        if (changed) be.addViewer(player);
        return changed;
    }

    private static boolean handleLeave(BlackjackTableBlockEntity be, ServerPlayerEntity player) {
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

    private static boolean handleStart(BlackjackTableBlockEntity be, ServerPlayerEntity player) {
        var server = be.getWorld() != null ? be.getWorld().getServer() : null;
        for (String name : be.getGame().kickPlayersBelowMin(server)) {
            be.removeViewerByName(name);
        }
        return be.getGame().startRound(server);
    }

    private static final String[] TRADE_ALLOWED = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond"};

    private static boolean handleDeposit(BlackjackTableBlockEntity be, ServerPlayerEntity player, int amount, String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (!java.util.Arrays.asList(TRADE_ALLOWED).contains(itemId)) return false;
        var trades = TradeConfig.get();
        int rate = trades.buyRates.getOrDefault(itemId, 0);
        if (rate <= 0) return false;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return false;
        Item targetItem = Registries.ITEM.get(id);
        int available = countItems(player, targetItem);
        if (available <= 0) return false;
        int toUse = (amount > 0) ? Math.min(amount, available) : available;
        removeItems(player, targetItem, toUse);
        int gained = toUse * rate;
        ZCoinStorage.add(player, gained);
        be.getGame().setStatusMessage(player.getName().getString() + " deposited +" + gained + " ZC.");
        return true;
    }

    private static boolean handleWithdraw(BlackjackTableBlockEntity be, ServerPlayerEntity player, int amount, String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (!java.util.Arrays.asList(TRADE_ALLOWED).contains(itemId)) return false;
        var trades = TradeConfig.get();
        int rate = trades.sellRates.getOrDefault(itemId, 0);
        int gives = trades.sellGives.getOrDefault(itemId, rate);
        if (rate <= 0) return false;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return false;
        Item targetItem = Registries.ITEM.get(id);
        int units = Math.max(1, amount);
        int totalCost = units * rate;
        int toGive = units * gives;
        if (!ZCoinStorage.deduct(player, totalCost)) return false;
        int remaining = toGive;
        while (remaining > 0) {
            int stack = Math.min(remaining, 64);
            ItemStack give = new ItemStack(targetItem, stack);
            if (!player.getInventory().insertStack(give)) player.dropItem(give, false);
            remaining -= stack;
        }
        be.getGame().setStatusMessage(player.getName().getString() + " withdrew " + toGive + "× " + itemId.split(":")[1] + ".");
        return true;
    }

    private static int countItems(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        if (player.getOffHandStack().getItem() == item) count += player.getOffHandStack().getCount();
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
        if (remaining > 0 && player.getOffHandStack().getItem() == item) {
            int take = Math.min(player.getOffHandStack().getCount(), remaining);
            player.getOffHandStack().decrement(take);
        }
    }
}
