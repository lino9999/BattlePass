package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Accounts eligible elapsed time once, retaining partial minutes between samples. */
final class PlayTimeTracker {
    private static final long MINUTE_MILLIS = 60000;
    private static final long MAX_PENDING_MILLIS = Integer.MAX_VALUE * MINUTE_MILLIS + MINUTE_MILLIS - 1;
    private final BattlePass plugin;
    private final LongSupplier clock;
    private final Map<UUID, Long> startTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingMillis = new ConcurrentHashMap<>();
    private final Map<UUID, String> worlds = new ConcurrentHashMap<>();

    PlayTimeTracker(BattlePass plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    void initialize(Player player) {
        UUID uuid = player.getUniqueId();
        startTimes.put(uuid, clock.getAsLong());
        pendingMillis.remove(uuid);
        worlds.put(uuid, player.getWorld().getName());
    }

    void update(Player player) {
        UUID uuid = player.getUniqueId();
        long now = clock.getAsLong();
        Long start = startTimes.putIfAbsent(uuid, now);
        String currentWorld = player.getWorld().getName();
        String previousWorld = worlds.put(uuid, currentWorld);
        if (start == null) return;

        long elapsed = 0;
        if (now >= start) {
            try {
                elapsed = Math.min(MAX_PENDING_MILLIS, Math.subtractExact(now, start));
            } catch (ArithmeticException overflow) {
                elapsed = MAX_PENDING_MILLIS;
            }
            startTimes.put(uuid, now);
        }

        long pending = pendingMillis.getOrDefault(uuid, 0L);
        if (!plugin.getConfigManager().isMissionWorldDisabled(previousWorld == null ? currentWorld : previousWorld)) {
            pending = Math.min(MAX_PENDING_MILLIS, pending + elapsed);
        }

        int minutes = 0;
        if (pending >= MINUTE_MILLIS
                && !plugin.getConfigManager().isMissionWorldDisabled(currentWorld)
                && !plugin.getMissionManager().isProgressPaused()
                && plugin.getPlayerDataManager().getPlayerData(uuid) != null) {
            minutes = (int) (pending / MINUTE_MILLIS);
            pending -= minutes * MINUTE_MILLIS;
        }
        if (pending == 0) pendingMillis.remove(uuid);
        else pendingMillis.put(uuid, pending);
        // Progress can fire external events that re-enter this tracker.
        if (minutes > 0) plugin.getMissionManager().progressMission(player, "PLAY_TIME", "ANY", minutes);
    }

    void cleanup(UUID uuid) {
        startTimes.remove(uuid);
        pendingMillis.remove(uuid);
        worlds.remove(uuid);
    }

    Map<UUID, Long> getStartTimes() {
        return startTimes;
    }
}
