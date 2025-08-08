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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionListener implements Listener {

    private final BattlePass plugin;
    private final Map<UUID, Long> playTimeStart = new ConcurrentHashMap<>();

    public PlayerConnectionListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        playTimeStart.put(uuid, System.currentTimeMillis());

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
                    int available = plugin.getRewardManager().countAvailableRewards(player, data);
                    if (available > 0) {
                        player.sendMessage(plugin.getMessageManager().getPrefix() +
                                plugin.getMessageManager().getMessage("messages.rewards-available",
                                        "%amount%", String.valueOf(available)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                plugin.getSoundManager().checkAndUpdateSound(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Long startTime = playTimeStart.remove(uuid);
        if (startTime != null) {
            long playTime = (System.currentTimeMillis() - startTime) / 60000;
            if (playTime > 0) {
                plugin.getMissionManager().progressMission(event.getPlayer(), "PLAY_TIME", "ANY", (int) playTime);
            }
        }

        plugin.getSoundManager().stopItemSound(uuid);
        plugin.getMissionManager().clearPlayerActionbars(uuid);
        plugin.getPlayerDataManager().removePlayer(uuid);
        plugin.getGuiManager().getCurrentPages().remove(event.getPlayer().getEntityId());

        if (plugin.getMissionProgressListener() != null) {
            plugin.getMissionProgressListener().cleanupPlayer(uuid);
        }
    }

    public Map<UUID, Long> getPlayTimeStart() {
        return playTimeStart;
    }
}