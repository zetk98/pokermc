package com.pokermc.xoso.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pokermc.PokerMod;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persistent storage for lottery draw results per day.
 * Stored in overworld so all blocks on the server share the same results.
 */
public class LotteryDrawPersistentState extends PersistentState {

    private final Map<Long, LotteryDrawStorage.DrawResult> draws;

    public LotteryDrawPersistentState() {
        this(new HashMap<>());
    }

    public LotteryDrawPersistentState(Map<Long, LotteryDrawStorage.DrawResult> draws) {
        this.draws = draws;
    }

    public LotteryDrawStorage.DrawResult get(long day) {
        return draws.get(day);
    }

    public void put(long day, LotteryDrawStorage.DrawResult result) {
        draws.put(day, result);
        markDirty();
    }

    private record DayEntry(long day, String special, String first, String second, String third) {}

    private static final Codec<DayEntry> DAY_ENTRY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.fieldOf("day").forGetter(DayEntry::day),
            Codec.STRING.fieldOf("special").forGetter(DayEntry::special),
            Codec.STRING.fieldOf("first").forGetter(DayEntry::first),
            Codec.STRING.fieldOf("second").forGetter(DayEntry::second),
            Codec.STRING.fieldOf("third").forGetter(DayEntry::third)
    ).apply(inst, DayEntry::new));

    private static final Codec<Map<Long, LotteryDrawStorage.DrawResult>> MAP_CODEC =
            Codec.list(DAY_ENTRY_CODEC).xmap(
                    list -> list.stream().collect(Collectors.toMap(
                            DayEntry::day,
                            e -> new LotteryDrawStorage.DrawResult(e.special(), e.first(), e.second(), e.third())
                    )),
                    map -> map.entrySet().stream()
                            .map(e -> new DayEntry(e.getKey(), e.getValue().special(), e.getValue().first(),
                                    e.getValue().second(), e.getValue().third()))
                            .collect(Collectors.toList())
            );

    private static final Codec<LotteryDrawPersistentState> CODEC =
            MAP_CODEC.xmap(LotteryDrawPersistentState::new, s -> s.draws);

    private static final PersistentStateType<LotteryDrawPersistentState> TYPE =
            new PersistentStateType<>(
                    PokerMod.MOD_ID + "_lottery_draws",
                    LotteryDrawPersistentState::new,
                    CODEC,
                    DataFixTypes.LEVEL
            );

    public static LotteryDrawPersistentState get(MinecraftServer server) {
        if (server == null) return null;
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return null;
        return overworld.getPersistentStateManager().getOrCreate(TYPE);
    }
}
