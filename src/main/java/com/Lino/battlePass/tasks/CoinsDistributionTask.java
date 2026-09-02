package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class CoinsDistributionTask extends BukkitRunnable {

    private final BattlePass plugin;
    private LocalDateTime nextDistribution;

    public CoinsDistributionTask(BattlePass plugin) {
        this.plugin = plugin;
        calculateNextDistribution();
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isShopEnabled()) {
            return;
        }

        if (LocalDateTime.now().isAfter(nextDistribution)) {
            distributeCoins();
            calculateNextDistribution();
            plugin.getDatabaseManager().saveCoinsDistributionTime(nextDistribution);
        }
    }

    private void distributeCoins() {
        plugin.getDatabaseManager().getTop10Players().thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Integer> coinAmounts = plugin.getConfigManager().getCoinsDistribution();

                if (topPlayers.isEmpty() || coinAmounts.isEmpty()) {
                    plugin.getLogger().info("Coins distribution skipped: no eligible players.");
                    return;
                }

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
                }

                Bukkit.broadcastMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.coins.distribution-announcement"));

                for (int i = 0; i < topPlayers.size() && i < coinAmounts.size(); i++) {
                    PlayerData topPlayer = topPlayers.get(i);
                    int coins = coinAmounts.get(i);

                    PlayerData onlineData = plugin.getPlayerDataManager().getPlayerData(topPlayer.uuid);
                    if (onlineData != null) {
                        onlineData.battleCoins += coins;
                        plugin.getPlayerDataManager().markForSave(topPlayer.uuid);
                    } else {
                        topPlayer.battleCoins += coins;
                        plugin.getDatabaseManager().updatePlayerCoins(topPlayer.uuid, topPlayer.battleCoins);
                    }

                    String playerName = Bukkit.getOfflinePlayer(topPlayer.uuid).getName();
                    if (playerName == null) {
                        playerName = "Unknown";
                    }
                    String rank = String.valueOf(i + 1);

                    Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("messages.coins.distribution-player",
                            "%rank%", rank,
                            "%player%", playerName,
                            "%amount%", String.valueOf(coins)));

                    Player player = Bukkit.getPlayer(topPlayer.uuid);
                    if (player != null) {
                        player.sendMessage(plugin.getMessageManager().getPrefix() +
                                plugin.getMessageManager().getMessage("messages.coins.received",
                                        "%amount%", String.valueOf(coins)));
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            });
        });
    }

    private void calculateNextDistribution() {
        LocalDateTime now = LocalDateTime.now();
        int hoursInterval = plugin.getConfigManager().getCoinsDistributionHours();
        nextDistribution = now.plusHours(hoursInterval);
    }

    public void resetDistributionTime() {
        calculateNextDistribution();
        plugin.getDatabaseManager().saveCoinsDistributionTime(nextDistribution);
    }

    public String getTimeUntilNextDistribution() {
        if (nextDistribution == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        long totalMinutes = Math.max(0, ChronoUnit.MINUTES.between(now, nextDistribution));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        return plugin.getMessageManager().getMessage("time.hours-minutes",
                "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    public LocalDateTime getNextDistribution() {
        return nextDistribution;
    }

    public void setNextDistribution(LocalDateTime time) {
        this.nextDistribution = time;
    }
}