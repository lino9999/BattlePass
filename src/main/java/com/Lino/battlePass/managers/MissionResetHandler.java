package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CompletableFuture;

public class MissionResetHandler {

    private final BattlePass plugin;
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonStartDate;
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

            LocalDateTime lastReset = nextMissionReset.minusHours(hoursInterval);

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
        return seasonEndDate != null && !LocalDateTime.now().isBefore(seasonEndDate);
    }

    public CompletableFuture<Void> resetSeason() {
        return resetSeason(false);
    }

    public CompletableFuture<Void> forceResetSeason() {
        return resetSeason(true);
    }

    private CompletableFuture<Void> resetSeason(boolean forced) {
        // Publish the new schedule before MissionManager saves it or checks for another reset.
        calculateSeasonEndDate();
        calculateNextReset();
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage(
                    forced ? "messages.season.forced-reset" : "messages.season.reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        broadcastNewSeason();

        plugin.getPlayerDataManager().clearCache(true);

        return plugin.getDatabaseManager().resetSeason().thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
                }
            });
        });
    }

    private void broadcastNewSeason() {
        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        if (rotation != null && rotation.isRotationEnabled()) {
            MessageManager messageManager = plugin.getMessageManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(messageManager.getPrefix() +
                        messageManager.getMessage("messages.season.new-season",
                                "%season%", String.valueOf(rotation.getCurrentSeason())));
            }
        }
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
        seasonStartDate = LocalDateTime.now();
        recalculateSeasonEndDate();
    }

    public void restoreSeasonDates(LocalDateTime startDate, LocalDateTime endDate) {
        seasonStartDate = startDate;
        seasonEndDate = endDate;
        if (seasonStartDate == null) {
            // Old databases only stored the deadline, so the original start cannot be recovered.
            // Give an existing duration season its configured time without clearing player progress.
            if ("MONTH_START".equalsIgnoreCase(plugin.getConfigManager().getSeasonResetType())) {
                seasonStartDate = seasonEndDate.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
            } else {
                seasonStartDate = LocalDateTime.now();
                plugin.getLogger().warning("Legacy season has no start date. Starting its configured " +
                        plugin.getConfigManager().getSeasonDuration() + "-day countdown now, preserving player progress. " +
                        "This migration runs once; previous deadline: " + seasonEndDate);
            }
        }
        recalculateSeasonEndDate();
    }

    public void recalculateSeasonEndDate() {
        if (seasonStartDate == null) return;
        String resetType = plugin.getConfigManager().getSeasonResetType();

        if (resetType.equalsIgnoreCase("MONTH_START")) {
            seasonEndDate = seasonStartDate.plusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
        } else {
            seasonEndDate = seasonStartDate.plusDays(plugin.getConfigManager().getSeasonDuration());
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
        long days = Math.max(0, ChronoUnit.DAYS.between(now, seasonEndDate));
        long hours = Math.max(0, ChronoUnit.HOURS.between(now, seasonEndDate)) % 24;

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

    public LocalDateTime getSeasonStartDate() {
        return seasonStartDate;
    }
}
