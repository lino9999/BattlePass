package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public class PlayerConnectionListener implements Listener {

    private final BattlePass plugin;
    private final PlayTimeTracker playTime;
    private final Map<UUID, JoinNotification> pendingJoins = new HashMap<>();
    private boolean feedbackStopped;

    public PlayerConnectionListener(BattlePass plugin) {
        this(plugin, System::currentTimeMillis);
    }

    PlayerConnectionListener(BattlePass plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.playTime = new PlayTimeTracker(plugin, clock);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (feedbackStopped || plugin.isStopping() || !plugin.isEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        cancelJoinNotification(uuid);

        initializePlayTime(player);

        if (plugin.getMissionProgressListener() != null) {
            plugin.getMissionProgressListener().initializePlayerLocation(uuid, player.getLocation());
        }

        plugin.getPlayerDataManager().loadPlayer(uuid);
        if (feedbackStopped || plugin.isStopping() || !plugin.isEnabled()) return;

        JoinNotification notification = new JoinNotification(player);
        pendingJoins.put(uuid, notification);
        try {
            notification.task = Bukkit.getScheduler().runTaskLater(plugin, notification, 40L);
            if (notification.finished) notification.task.cancel();
        } catch (RuntimeException failure) {
            pendingJoins.remove(uuid, notification);
            notification.cancel();
            if (!feedbackStopped && !plugin.isStopping() && plugin.isEnabled())
                plugin.getLogger().warning("Could not schedule join feedback: " + failure.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelJoinNotification(uuid);

        updatePlayTime(event.getPlayer());
        playTime.cleanup(uuid);
        if (plugin.getRewardEditorManager() != null) plugin.getRewardEditorManager().cleanupPlayer(uuid);
        if (plugin.getMissionEditorManager() != null) plugin.getMissionEditorManager().cleanupPlayer(uuid);

        plugin.getXpEventManager().removePlayerFromBossBar(event.getPlayer());
        plugin.getSoundManager().stopItemSound(uuid);
        plugin.getMissionManager().clearPlayerActionbars(uuid);
        plugin.getPlayerDataManager().removePlayer(uuid);
        plugin.getGuiManager().getCurrentPages().remove(event.getPlayer().getEntityId());

        if (plugin.getMissionProgressListener() != null) {
            plugin.getMissionProgressListener().cleanupPlayer(uuid);
        }
    }

    /** Stop presentation only: final play-time progress is still flushed afterwards during disable. */
    public void stopJoinFeedback() {
        feedbackStopped = true;
        for (UUID uuid : List.copyOf(pendingJoins.keySet())) cancelJoinNotification(uuid);
    }

    private void cancelJoinNotification(UUID uuid) {
        JoinNotification previous = pendingJoins.remove(uuid);
        if (previous != null) previous.cancel();
    }

    private final class JoinNotification implements Runnable {
        private final Player player;
        private final UUID uuid;
        private BukkitTask task;
        private boolean finished;

        private JoinNotification(Player player) { this.player = player; uuid = player.getUniqueId(); }

        private boolean isCurrent() {
            return pendingJoins.get(uuid) == this && !feedbackStopped && !plugin.isStopping()
                    && plugin.isEnabled() && player.isOnline();
        }

        private void cancel() {
            finished = true;
            if (task != null) {
                try { task.cancel(); }
                catch (RuntimeException failure) { plugin.getLogger().warning("Could not cancel join feedback: " + failure.getMessage()); }
            }
        }

        @Override public void run() {
            if (finished) return;
            finished = true;
            try {
                if (!isCurrent()) return;
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (!isCurrent()) return;
                if (data != null) {
                    int available = plugin.getRewardManager().countAvailableRewards(data);
                    if (!isCurrent()) return;
                    if (available > 0) {
                        String message = plugin.getMessageManager().getPrefix() +
                                plugin.getMessageManager().getMessage("messages.rewards-available", "%amount%", String.valueOf(available));
                        if (!isCurrent()) return;
                        player.sendMessage(message);
                        if (!isCurrent()) return;
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                if (isCurrent()) plugin.getSoundManager().checkAndUpdateSound(player);
                if (isCurrent()) plugin.getXpEventManager().addPlayerToBossBar(player);
            } finally {
                pendingJoins.remove(uuid, this);
            }
        }
    }

    public Map<UUID, Long> getPlayTimeStart() {
        return playTime.getStartTimes();
    }

    void initializePlayTime(Player player) {
        playTime.initialize(player);
    }

    public void updatePlayTime(Player player) {
        playTime.update(player);
    }

    Object beginPlayTimeCycle() { return playTime.beginCycle(); }
    void commitPlayTimeCycle(Object token) { playTime.commitCycle(token); }
    void abortPlayTimeCycle(Object token) { playTime.abortCycle(token); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        updatePlayTime(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled() && event.getTo() != null
                && !Objects.equals(event.getFrom().getWorld(), event.getTo().getWorld())) {
            updatePlayTime(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!event.getPlayer().getWorld().equals(event.getRespawnLocation().getWorld())) {
            updatePlayTime(event.getPlayer());
        }
    }
}
