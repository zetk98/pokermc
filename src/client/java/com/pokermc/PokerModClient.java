package com.pokermc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokermc.bang.network.BangNetworking;
import com.pokermc.blackjack.network.BlackjackNetworking;
import com.pokermc.common.network.CloseGamePayload;
import com.pokermc.poker.network.PokerNetworking;
import com.pokermc.bang.screen.BangLobbyScreen;
import com.pokermc.bang.screen.BangTableScreen;
import com.pokermc.blackjack.screen.BlackjackLobbyScreen;
import com.pokermc.blackjack.screen.BlackjackTableScreen;
import com.pokermc.blackjack.screen.CreateBlackjackRoomScreen;
import com.pokermc.poker.screen.CreateRoomScreen;
import com.pokermc.poker.screen.PokerLobbyScreen;
import com.pokermc.poker.screen.PokerTableScreen;
import com.pokermc.goldenticket.network.GoldenTicketNetworking;
import com.pokermc.goldenticket.screen.GoldenTicketScreen;
import com.pokermc.market.network.MarketNetworking;
import com.pokermc.market.screen.MarketScreen;
import com.pokermc.xoso.network.XosoNetworking;
import com.pokermc.xoso.screen.XosoTableScreen;
import com.pokermc.common.screen.TradeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;

public class PokerModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ZCoin Bag dùng vanilla Generic3x3ContainerScreen (GENERIC_3X3) - không cần register

        ClientPlayNetworking.registerGlobalReceiver(
                PokerNetworking.OpenTablePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            String myName = context.client().player != null
                                    ? context.client().player.getName().getString() : "";
                            boolean inGame = false;
                            try {
                                JsonObject obj = JsonParser.parseString(payload.stateJson()).getAsJsonObject();
                                if (obj.has("players")) {
                                    for (JsonElement e : obj.getAsJsonArray("players")) {
                                        if (e.getAsJsonObject().get("name").getAsString().equals(myName)) {
                                            inGame = true;
                                            break;
                                        }
                                    }
                                }
                                if (!inGame && obj.has("pendingPlayers")) {
                                    for (JsonElement e : obj.getAsJsonArray("pendingPlayers")) {
                                        if (e.getAsString().equals(myName)) { inGame = true; break; }
                                    }
                                }
                            } catch (Exception ignored) {}
                            if (inGame) {
                                PokerTableScreen ts = new PokerTableScreen(payload.pos(), payload.stateJson());
                                context.client().setScreen(ts);
                                ts.updateState(payload.stateJson());
                            } else {
                                PokerLobbyScreen lobby = new PokerLobbyScreen(payload.pos(), payload.stateJson());
                                context.client().setScreen(lobby);
                            }
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft Client] Error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                MarketNetworking.OpenMarketPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            MarketScreen screen = new MarketScreen(payload.pos(), payload.stateJson());
                            context.client().setScreen(screen);
                            screen.updateState(payload.stateJson());
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft] Market error: " + e.getMessage());
                        }
                    });
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
                MarketNetworking.MarketStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    if (s instanceof MarketScreen ms && ms.getTablePos().equals(payload.pos())) {
                        ms.updateState(payload.stateJson());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                GoldenTicketNetworking.OpenGoldenTicketPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            GoldenTicketScreen screen = new GoldenTicketScreen(payload.pos(), payload.stateJson());
                            context.client().setScreen(screen);
                            screen.updateState(payload.stateJson());
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft] Golden Ticket error: " + e.getMessage());
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                XosoNetworking.OpenXosoPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            XosoTableScreen screen = new XosoTableScreen(payload.pos(), payload.stateJson());
                            context.client().setScreen(screen);
                            screen.updateState(payload.stateJson());
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft] XOSO error: " + e.getMessage());
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BangNetworking.OpenBangPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            BangLobbyScreen lobby = new BangLobbyScreen(payload.pos(), payload.stateJson());
                            context.client().setScreen(lobby);
                            lobby.updateState(payload.stateJson());
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft] Bang error: " + e.getMessage());
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BlackjackNetworking.OpenBlackjackPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        try {
                            String myName = context.client().player != null
                                    ? context.client().player.getName().getString() : "";
                            boolean inGame = false;
                            try {
                                JsonObject obj = JsonParser.parseString(payload.stateJson()).getAsJsonObject();
                                if (obj.has("players")) {
                                    for (JsonElement e : obj.getAsJsonArray("players")) {
                                        if (e.getAsJsonObject().get("name").getAsString().equals(myName)) {
                                            inGame = true;
                                            break;
                                        }
                                    }
                                }
                                if (!inGame && obj.has("pendingPlayers")) {
                                    for (JsonElement e : obj.getAsJsonArray("pendingPlayers")) {
                                        if (e.getAsString().equals(myName)) { inGame = true; break; }
                                    }
                                }
                            } catch (Exception ignored) {}
                            if (inGame) {
                                BlackjackTableScreen ts = new BlackjackTableScreen(payload.pos(), payload.stateJson());
                                context.client().setScreen(ts);
                                ts.updateState(payload.stateJson());
                            } else {
                                BlackjackLobbyScreen lobby = new BlackjackLobbyScreen(payload.pos(), payload.stateJson());
                                context.client().setScreen(lobby);
                            }
                        } catch (Exception e) {
                            System.err.println("[CasinoCraft Client] Blackjack error: " + e.getMessage());
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                PokerNetworking.GameStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    if      (s instanceof PokerTableScreen pts) pts.updateState(payload.stateJson());
                    else if (s instanceof PokerLobbyScreen pls) pls.updateState(payload.stateJson());
                    else if (s instanceof TradeScreen       ts)  ts.updateState(payload.stateJson());
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BlackjackNetworking.BlackjackStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    if      (s instanceof BlackjackTableScreen bts) bts.updateState(payload.stateJson());
                    else if (s instanceof BlackjackLobbyScreen bls) bls.updateState(payload.stateJson());
                    else if (s instanceof TradeScreen ts) ts.updateState(payload.stateJson());
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BangNetworking.BangStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    String phase = "";
                    boolean inGame = false;
                    try {
                        var obj = com.google.gson.JsonParser.parseString(payload.stateJson()).getAsJsonObject();
                        phase = obj.has("phase") ? obj.get("phase").getAsString() : "";
                        String myName = context.client().player != null ? context.client().player.getName().getString() : "";
                        if (obj.has("players")) {
                            for (com.google.gson.JsonElement e : obj.getAsJsonArray("players")) {
                                if (e.getAsJsonObject().get("name").getAsString().equals(myName)) {
                                    inGame = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    // GAME_OVER: stay on BangTableScreen to show overlay + New Game button
                    if (("ROLE_REVEAL".equals(phase) || "DEALING".equals(phase) || "DEAL_FIRST".equals(phase) || "PLAYING".equals(phase) || "DISCARD".equals(phase) || "NEXT_TURN_DELAY".equals(phase) || "GAME_OVER".equals(phase)) && s instanceof BangLobbyScreen && inGame) {
                        BangTableScreen ts = new BangTableScreen(payload.pos(), payload.stateJson());
                        context.client().setScreen(ts);
                        ts.updateState(payload.stateJson());
                    } else if (s instanceof BangTableScreen bts) {
                        bts.updateState(payload.stateJson());
                    } else if (s instanceof BangLobbyScreen bls) {
                        bls.updateState(payload.stateJson());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                XosoNetworking.XosoStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    net.minecraft.client.gui.screen.Screen s = context.client().currentScreen;
                    if (s instanceof XosoTableScreen xts) xts.updateState(payload.stateJson());
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CloseGamePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    if (s == null) return;
                    net.minecraft.util.math.BlockPos screenPos = null;
                    if (s instanceof PokerTableScreen pts) screenPos = pts.getTablePos();
                    else if (s instanceof PokerLobbyScreen pls) screenPos = pls.getTablePos();
                    else if (s instanceof BlackjackTableScreen bts) screenPos = bts.getTablePos();
                    else if (s instanceof BlackjackLobbyScreen bls) screenPos = bls.getTablePos();
                    else if (s instanceof BangTableScreen bts2) screenPos = bts2.getTablePos();
                    else if (s instanceof BangLobbyScreen bls2) screenPos = bls2.getTablePos();
                    else if (s instanceof XosoTableScreen xts) screenPos = xts.getTablePos();
                    else if (s instanceof MarketScreen ms) screenPos = ms.getTablePos();
                    else if (s instanceof GoldenTicketScreen gts) screenPos = gts.getTablePos();
                    else if (s instanceof TradeScreen ts) screenPos = ts.getTablePos();
                    else if (s instanceof CreateRoomScreen crs) screenPos = crs.getTablePos();
                    else if (s instanceof CreateBlackjackRoomScreen cbrs) screenPos = cbrs.getTablePos();
                    if (screenPos != null && screenPos.equals(payload.pos())) {
                        context.client().setScreen(null);
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                com.pokermc.common.network.CloseScreenPayload.ID,
                (payload, context) -> context.client().execute(() -> context.client().setScreen(null))
        );

        System.out.println("[CasinoCraft Client] Client initialized.");
    }
}
