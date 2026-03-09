package com.pokermc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokermc.network.BangNetworking;
import com.pokermc.network.BlackjackNetworking;
import com.pokermc.network.PokerNetworking;
import com.pokermc.screen.BangLobbyScreen;
import com.pokermc.screen.BangTableScreen;
import com.pokermc.screen.BlackjackLobbyScreen;
import com.pokermc.screen.BlackjackTableScreen;
import com.pokermc.screen.PokerLobbyScreen;
import com.pokermc.screen.PokerTableScreen;
import com.pokermc.screen.TradeScreen;
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
                    if ("GAME_OVER".equals(phase) && s instanceof BangTableScreen) {
                        BangLobbyScreen lobby = new BangLobbyScreen(payload.pos(), payload.stateJson());
                        context.client().setScreen(lobby);
                        lobby.updateState(payload.stateJson());
                    } else if (("ROLE_REVEAL".equals(phase) || "DEALING".equals(phase) || "DEAL_FIRST".equals(phase) || "PLAYING".equals(phase) || "DISCARD".equals(phase) || "NEXT_TURN_DELAY".equals(phase)) && s instanceof BangLobbyScreen && inGame) {
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

        System.out.println("[CasinoCraft Client] Client initialized.");
    }
}
