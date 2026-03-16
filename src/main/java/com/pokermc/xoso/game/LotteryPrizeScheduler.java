package com.pokermc.xoso.game;

import com.pokermc.common.config.ZCoinStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules lottery prize delivery 2 seconds after claim.
 * Fixes items disappearing when given immediately during item use.
 */
public class LotteryPrizeScheduler {

    private static final int DELAY_TICKS = 40; // 2 seconds

    private record PendingPrize(ServerPlayerEntity player, int amount, String prizeName, long period, int ticksLeft) {}

    private static final Map<UUID, PendingPrize> PENDING = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LotteryPrizeScheduler::tick);
    }

    public static void schedule(ServerPlayerEntity player, int amount, String prizeName, long period) {
        if (amount <= 0) return;
        UUID uuid = player.getUuid();
        var existing = PENDING.get(uuid);
        if (existing != null) {
            // Gộp giải khi claim nhiều vé trong 2 giây - tránh mất giải trước
            String mergedName = existing.prizeName().equals(prizeName) ? prizeName : "Multiple";
            PENDING.put(uuid, new PendingPrize(player, existing.amount() + amount, mergedName, period, existing.ticksLeft()));
        } else {
            PENDING.put(uuid, new PendingPrize(player, amount, prizeName, period, DELAY_TICKS));
        }
    }

    private static void tick(MinecraftServer server) {
        var toProcess = new java.util.ArrayList<Map.Entry<UUID, PendingPrize>>();
        PENDING.entrySet().removeIf(entry -> {
            PendingPrize p = entry.getValue();
            if (p.player() == null || !p.player().isAlive() || p.player().isDisconnected()) return true;
            if (p.ticksLeft() <= 1) {
                toProcess.add(entry);
                return true;
            }
            // Chỉ replace value, KHÔNG remove - nếu remove sau put sẽ xóa entry mới vừa thêm (mất giải)
            PENDING.put(entry.getKey(), new PendingPrize(p.player(), p.amount(), p.prizeName(), p.period(), p.ticksLeft() - 1));
            return false;
        });
        for (var entry : toProcess) {
            PendingPrize p = entry.getValue();
            ZCoinStorage.add(p.player(), p.amount());
            p.player().sendMessage(Text.literal("§aCongratulations! You won " + p.prizeName() + " prize: " + p.amount() + " ZC!"), true);
            Text broadcast = Text.literal("§6[Lottery] §f" + p.player().getName().getString() + " §ewon the " + p.prizeName() + " prize §f(#" + p.period() + ") §f(" + p.amount() + " ZC)!");
            server.getPlayerManager().getPlayerList().forEach(pl -> pl.sendMessage(broadcast, false));
        }
    }
}
