package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BattlePassTask extends BukkitRunnable {

    private final BattlePass plugin;
    private long lastCleanupTime = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 300000;

    public BattlePassTask(BattlePass plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getPlayerDataManager().saveAllPlayers();
        plugin.getMissionManager().checkMissionReset();
        plugin.getMissionManager().checkSeasonReset();
        checkRewardNotifications();
        updatePlayTime();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime >= CLEANUP_INTERVAL) {
            performPeriodicCleanup();
            lastCleanupTime = currentTime;
        }
    }

    private void performPeriodicCleanup() {
        plugin.getPlayerDataManager().cleanupStaleEntries();
        plugin.getGuiManager().cleanExpiredCachePublic();
    }

    private void checkRewardNotifications() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data == null) continue;

            int availableRewards = plugin.getRewardManager().countAvailableRewards(data);

            if (availableRewards > data.lastNotification) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.rewards-notification",
                                "%amount%", String.valueOf(availableRewards - data.lastNotification)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                data.lastNotification = availableRewards;
                plugin.getPlayerDataManager().markForSave(player.getUniqueId());
            } else if (availableRewards < data.lastNotification) {
                data.lastNotification = availableRewards;
                plugin.getPlayerDataManager().markForSave(player.getUniqueId());
            }
        }
    }

    private void updatePlayTime() {
        if (plugin.getEventManager() == null || plugin.getEventManager().getPlayerConnectionListener() == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getEventManager().getPlayerConnectionListener().updatePlayTime(player);
        }
    }
}
