package com.pokermc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokermc.network.PokerNetworking;
import com.pokermc.screen.PokerLobbyScreen;
import com.pokermc.screen.PokerTableScreen;
import com.pokermc.screen.TradeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;

public class PokerModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

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
                            System.err.println("[PokerMC Client] Error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
        );

        // S2C: Game state update — push to whichever screen is open
        ClientPlayNetworking.registerGlobalReceiver(
                PokerNetworking.GameStatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Screen s = context.client().currentScreen;
                    if      (s instanceof PokerTableScreen pts) pts.updateState(payload.stateJson());
                    else if (s instanceof PokerLobbyScreen pls) pls.updateState(payload.stateJson());
                    else if (s instanceof TradeScreen       ts)  ts.updateState(payload.stateJson());
                    // CreateRoomScreen doesn't need state updates
                })
        );

        System.out.println("[PokerMC Client] Client initialized.");
    }
}
