package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class MissionResetHandler {

    private final BattlePass plugin;
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;

    public MissionResetHandler(BattlePass plugin) {
        this.plugin = plugin;
    }

    public void calculateNextReset() {
        LocalDateTime now = LocalDateTime.now();
        int hoursInterval = plugin.getConfigManager().getMissionResetHours();
        nextMissionReset = now.plusHours(hoursInterval);
    }

    public void recalculateResetTimeOnReload() {
        if (nextMissionReset != null) {
            LocalDateTime now = LocalDateTime.now();
            int hoursInterval = plugin.getConfigManager().getMissionResetHours();

            LocalDateTime lastReset = nextMissionReset.minusHours(24);

            while (lastReset.plusHours(hoursInterval).isBefore(now)) {
                lastReset = lastReset.plusHours(hoursInterval);
            }

            nextMissionReset = lastReset.plusHours(hoursInterval);
        }
    }

    public boolean shouldResetMissions() {
        return nextMissionReset != null && LocalDateTime.now().isAfter(nextMissionReset);
    }

    public boolean shouldResetSeason() {
        return seasonEndDate != null && LocalDateTime.now().isAfter(seasonEndDate);
    }

    public void resetSeason() {
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.season.reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        plugin.getPlayerDataManager().clearCache(false);

        plugin.getDatabaseManager().resetSeason().thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
                }
                calculateSeasonEndDate();
            });
        });
    }

    public void forceResetSeason() {
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.season.forced-reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        plugin.getPlayerDataManager().clearCache(false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getDatabaseManager().resetSeason().thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    calculateSeasonEndDate();
                    calculateNextReset();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
                    }

                    if (plugin.getCoinsDistributionTask() != null) {
                        plugin.getCoinsDistributionTask().cancel();
                        CoinsDistributionTask newTask = new CoinsDistributionTask(plugin);
                        newTask.runTaskTimer(plugin, 200L, 1200L);
                    }
                });
            });
        }, 20L);
    }

    public void forceResetMissions() {
        MessageManager messageManager = plugin.getMessageManager();

        calculateNextReset();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.admin-reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data != null) {
                data.missionProgress.clear();
                plugin.getPlayerDataManager().markForSave(player.getUniqueId());
            }
        }
    }

    public void calculateSeasonEndDate() {
        String resetType = plugin.getConfigManager().getSeasonResetType();

        if (resetType.equalsIgnoreCase("MONTH_START")) {
            seasonEndDate = LocalDateTime.now().plusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0);
        } else {
            seasonEndDate = LocalDateTime.now().plusDays(plugin.getConfigManager().getSeasonDuration());
        }
    }

    public String getTimeUntilReset() {
        if (nextMissionReset == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        long hours = ChronoUnit.HOURS.between(now, nextMissionReset);
        long minutes = ChronoUnit.MINUTES.between(now, nextMissionReset) % 60;

        MessageManager messageManager = plugin.getMessageManager();
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");
        String minuteStr = minutes == 1 ? messageManager.getMessage("time.minute") : messageManager.getMessage("time.minutes");

        return messageManager.getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    public String getTimeUntilSeasonEnd() {
        if (seasonEndDate == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(now, seasonEndDate);
        long hours = ChronoUnit.HOURS.between(now, seasonEndDate) % 24;

        MessageManager messageManager = plugin.getMessageManager();
        String dayStr = days == 1 ? messageManager.getMessage("time.day") : messageManager.getMessage("time.days");
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");

        return messageManager.getMessage("time.days-hours", "%days%", String.valueOf(days),
                "%hours%", String.valueOf(hours));
    }

    public String getTimeUntilDailyReward(long lastClaimed) {
        LocalDateTime lastClaimTime = LocalDateTime.ofEpochSecond(lastClaimed / 1000, 0, java.time.ZoneOffset.UTC);
        LocalDateTime nextAvailable = lastClaimTime.plusDays(1);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

        if (now.isAfter(nextAvailable)) {
            return plugin.getMessageManager().getMessage("time.available-now");
        }

        long hours = ChronoUnit.HOURS.between(now, nextAvailable);
        long minutes = ChronoUnit.MINUTES.between(now, nextAvailable) % 60;

        MessageManager messageManager = plugin.getMessageManager();
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");
        String minuteStr = minutes == 1 ? messageManager.getMessage("time.minute") : messageManager.getMessage("time.minutes");

        return messageManager.getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    public LocalDateTime getNextMissionReset() {
        return nextMissionReset;
    }

    public void setNextMissionReset(LocalDateTime nextMissionReset) {
        this.nextMissionReset = nextMissionReset;
    }

    public LocalDateTime getSeasonEndDate() {
        return seasonEndDate;
    }

    public void setSeasonEndDate(LocalDateTime seasonEndDate) {
        this.seasonEndDate = seasonEndDate;
    }
}