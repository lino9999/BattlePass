package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public class PlayerConnectionListener implements Listener {

    private final BattlePass plugin;
    private final PlayTimeTracker playTime;

    public PlayerConnectionListener(BattlePass plugin) {
        this(plugin, System::currentTimeMillis);
    }

    PlayerConnectionListener(BattlePass plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.playTime = new PlayTimeTracker(plugin, clock);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        initializePlayTime(player);

        if (plugin.getMissionProgressListener() != null) {
            plugin.getMissionProgressListener().initializePlayerLocation(uuid, player.getLocation());
        }

        plugin.getPlayerDataManager().loadPlayer(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (data != null) {
                    int available = plugin.getRewardManager().countAvailableRewards(data);
                    if (available > 0) {
                        player.sendMessage(plugin.getMessageManager().getPrefix() +
                                plugin.getMessageManager().getMessage("messages.rewards-available",
                                        "%amount%", String.valueOf(available)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                plugin.getSoundManager().checkAndUpdateSound(player);
                plugin.getXpEventManager().addPlayerToBossBar(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

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

    public Map<UUID, Long> getPlayTimeStart() {
        return playTime.getStartTimes();
    }

    void initializePlayTime(Player player) {
        playTime.initialize(player);
    }

    public void updatePlayTime(Player player) {
        playTime.update(player);
    }

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
