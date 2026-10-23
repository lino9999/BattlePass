package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.managers.DatabaseManager;
import com.Lino.battlePass.managers.MissionManager;
import com.Lino.battlePass.managers.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

public class CoinsDistributionTask extends BukkitRunnable {

    private final BattlePass plugin;
    private LocalDateTime nextDistribution;
    private volatile boolean distributionPending;
    private volatile boolean cancelled;

    public CoinsDistributionTask(BattlePass plugin) {
        this.plugin = plugin;
        calculateNextDistribution();
    }

    @Override
    public void run() {
        if (cancelled || distributionPending || !plugin.getConfigManager().isShopEnabled() || progressPaused()) return;
        if (nextDistribution == null || LocalDateTime.now().isBefore(nextDistribution)) return;
        distributionPending = true;
        LocalDateTime due = nextDistribution;
        try {
            plugin.getPlayerDataManager().saveAllPlayersSync();
            plugin.getDatabaseManager().getTop10Players().whenComplete((topPlayers, failure) -> {
                if (failure != null) {
                    if (!cancelled) reportFailure(failure);
                    distributionPending = false;
                    return;
                }
                if (cancelled) {
                    distributionPending = false;
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (!cancelled && due.equals(nextDistribution)
                                    && plugin.getConfigManager().isShopEnabled() && !progressPaused()) {
                                distributeCoins(topPlayers, due);
                            }
                        } catch (RuntimeException error) {
                            reportFailure(error);
                        } finally {
                            distributionPending = false;
                        }
                    });
                } catch (RuntimeException error) {
                    if (!cancelled) reportFailure(error);
                    distributionPending = false;
                }
            });
        } catch (RuntimeException failure) {
            distributionPending = false;
            reportFailure(failure);
        }
    }

    private boolean progressPaused() {
        MissionManager missions = plugin.getMissionManager();
        return missions != null && missions.isProgressPaused();
    }

    private void distributeCoins(List<PlayerData> topPlayers, LocalDateTime due) {
        List<Integer> coinAmounts = plugin.getConfigManager().getCoinsDistribution();
        Map<UUID, Integer> credits = new LinkedHashMap<>();
        for (int i = 0; i < topPlayers.size() && i < coinAmounts.size(); i++) {
            credits.put(topPlayers.get(i).uuid, Math.max(0, coinAmounts.get(i)));
        }
        LocalDateTime next = LocalDateTime.now().plusHours(Math.max(1, plugin.getConfigManager().getCoinsDistributionHours()));
        PlayerDataManager players = plugin.getPlayerDataManager();
        players.prepareCoinsDistribution();
        try {
            try {
                plugin.getDatabaseManager().commitCoinsDistribution(due, next, credits);
            } catch (DatabaseManager.CoinsDeadlineChangedException conflict) {
                nextDistribution = conflict.getNextDeadline();
                return;
            }
            // Cached balances remain unchanged until the complete database delivery has committed.
            for (var entry : credits.entrySet()) {
                PlayerData cached = players.getPlayerData(entry.getKey());
                if (cached != null) {
                    cached.addBattleCoins(entry.getValue());
                    players.markForSave(entry.getKey());
                }
            }
            nextDistribution = next;
            // Keep the existing deadline-save hook; the transaction already persisted this value.
            plugin.getDatabaseManager().saveCoinsDistributionTime(nextDistribution);
        } finally {
            players.finishCoinsDistribution();
        }

        if (credits.isEmpty()) {
            plugin.getLogger().info("Coins distribution skipped: no eligible players.");
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
        }
        Bukkit.broadcastMessage(plugin.getMessageManager().getPrefix()
                + plugin.getMessageManager().getMessage("messages.coins.distribution-announcement"));
        for (int i = 0; i < topPlayers.size() && i < coinAmounts.size(); i++) {
            UUID uuid = topPlayers.get(i).uuid;
            int coins = credits.getOrDefault(uuid, 0);
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = "Unknown";
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("messages.coins.distribution-player",
                    "%rank%", String.valueOf(i + 1), "%player%", playerName, "%amount%", String.valueOf(coins)));
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage(
                        "messages.coins.received", "%amount%", String.valueOf(coins)));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }

    private void reportFailure(Throwable failure) {
        plugin.getLogger().warning("Coin distribution error: " + failure.getMessage());
    }

    @Override
    public synchronized void cancel() {
        cancelled = true;
        super.cancel();
    }

    private void calculateNextDistribution() {
        LocalDateTime now = LocalDateTime.now();
        int hoursInterval = Math.max(1, plugin.getConfigManager().getCoinsDistributionHours());
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
