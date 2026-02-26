package com.pokermc;

import com.pokermc.network.PokerNetworking;
import com.pokermc.screen.CreateRoomScreen;
import com.pokermc.screen.PokerLobbyScreen;
import com.pokermc.screen.PokerTableScreen;
import com.pokermc.screen.TradeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;

public class PokerModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // S2C: Server wants to open the poker table screen → show lobby first
        ClientPlayNetworking.registerGlobalReceiver(
                PokerNetworking.OpenTablePayload.ID,
                (payload, context) -> {
                    System.out.println("[PokerMC Client] Received OpenTablePayload pos=" + payload.pos());
                    context.client().execute(() -> {
                        try {
                            PokerLobbyScreen lobby = new PokerLobbyScreen(payload.pos(), payload.stateJson());
                            context.client().setScreen(lobby);
                            System.out.println("[PokerMC Client] Lobby screen opened.");
                        } catch (Exception e) {
                            System.err.println("[PokerMC Client] Error opening lobby: " + e.getMessage());
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
