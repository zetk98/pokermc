package com.pokermc.goldenticket.blockentity;

import com.pokermc.PokerMod;
import com.pokermc.goldenticket.network.GoldenTicketNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GoldenTicketBlockEntity extends BlockEntity {

    public GoldenTicketBlockEntity(BlockPos pos, BlockState state) {
        super(PokerMod.GOLDEN_TICKET_BLOCK_ENTITY, pos, state);
    }

    public void openFor(ServerPlayerEntity player) {
        try {
            String json = GoldenTicketNetworking.serializeState(player);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new GoldenTicketNetworking.OpenGoldenTicketPayload(pos, json));
        } catch (Exception e) {
            System.err.println("[Golden Ticket] openFor failed: " + e.getMessage());
            player.sendMessage(net.minecraft.text.Text.literal("[Golden Ticket] Error: " + e.getMessage()), false);
        }
    }
}
