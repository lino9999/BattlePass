package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.listeners.EventManager;
import com.Lino.battlePass.listeners.PlayerConnectionListener;
import com.Lino.battlePass.managers.MissionManager;
import com.Lino.battlePass.managers.PlayerDataManager;
import com.Lino.battlePass.managers.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Server-thread periodic work, bound to one set of managers and closed before final shutdown flush. */
public class BattlePassTask extends BukkitRunnable {
    private static final long CLEANUP_INTERVAL = TimeUnit.MINUTES.toNanos(5);
    private final BattlePass plugin;
    private final PlayerDataManager players;
    private final MissionManager missions;
    private final RewardManager rewards;
    private final RewardNotifications notifications;
    private final LongSupplier clock;
    private long lastCleanupNanos;
    private boolean running;
    private boolean closed;

    public BattlePassTask(BattlePass plugin) {
        this(plugin, System::nanoTime);
    }

    BattlePassTask(BattlePass plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.players = plugin.getPlayerDataManager();
        this.missions = plugin.getMissionManager();
        this.rewards = plugin.getRewardManager();
        this.clock = clock;
        this.lastCleanupNanos = clock.getAsLong();
        this.notifications = new RewardNotifications(plugin, players, rewards,
                () -> isActive() && !missions.isProgressPaused());
    }

    @Override public void run() {
        if (running || !isActive()) return;
        running = true;
        try {
            players.saveAllPlayers();
            if (!isActive()) return;
            missions.checkSeasonReset();
            if (!isActive()) return;
            if (!missions.isProgressPaused()) missions.checkMissionReset();
            if (!isActive()) return;
            notifications.run();
            if (!isActive()) return;
            updatePlayTime();
            if (!isActive()) return;

            long now = clock.getAsLong();
            if (now - lastCleanupNanos >= CLEANUP_INTERVAL) {
                performPeriodicCleanup();
                lastCleanupNanos = now;
            }
        } finally {
            running = false;
        }
    }

    private boolean isActive() {
        return !closed && !plugin.isStopping() && plugin.isEnabled()
                && players != null && missions != null && rewards != null
                && plugin.getPlayerDataManager() == players && plugin.getMissionManager() == missions
                && plugin.getRewardManager() == rewards;
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        try { cancel(); }
        catch (IllegalStateException unscheduled) { /* Direct/manual tasks may never have been scheduled. */ }
        catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not cancel periodic BattlePass task: " + failure.getMessage());
        }
    }

    private void performPeriodicCleanup() {
        if (!isActive()) return;
        players.cleanupStaleEntries();
        if (isActive()) plugin.getGuiManager().cleanExpiredCachePublic();
    }

    private void updatePlayTime() {
        EventManager events = plugin.getEventManager();
        PlayerConnectionListener connections = events == null ? null : events.getPlayerConnectionListener();
        if (connections == null) return;
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!isActive()) return;
            if (!player.isOnline()) continue;
            try { connections.updatePlayTime(player); }
            catch (RuntimeException failure) {
                plugin.getLogger().warning("Could not update play time for " + player.getUniqueId() + ": " + failure.getMessage());
            }
        }
    }
}
