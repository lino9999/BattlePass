package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.ResetResult;
import com.Lino.battlePass.utils.DailyRewardCooldown;
import java.util.function.LongSupplier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CompletableFuture;

public class MissionResetHandler {

    private final BattlePass plugin;
    private final LongSupplier dailyRewardClock;
    private LocalDateTime nextMissionReset;
    private LocalDateTime missionCycleStart;
    private LocalDateTime seasonStartDate;
    private LocalDateTime seasonEndDate;

    public MissionResetHandler(BattlePass plugin) {
        this(plugin, System::currentTimeMillis);
    }

    MissionResetHandler(BattlePass plugin, LongSupplier dailyRewardClock) {
        this.plugin = plugin;
        this.dailyRewardClock = dailyRewardClock;
    }

    public void calculateNextReset() {
        LocalDateTime now = LocalDateTime.now();
        missionCycleStart = now;
        nextMissionReset = now.plusHours(missionIntervalHours());
    }

    public void recalculateResetTimeOnReload() {
        if (missionCycleStart != null) nextMissionReset = missionCycleStart.plusHours(missionIntervalHours());
    }

    public boolean shouldResetMissions() {
        return nextMissionReset != null && !LocalDateTime.now().isBefore(nextMissionReset);
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
        MissionManager manager = plugin.getMissionManager();
        if (manager == null) return CompletableFuture.failedFuture(new IllegalStateException("MissionManager is not ready"));
        return (forced ? manager.forceResetSeasonAsync() : manager.resetSeasonAsync()).thenCompose(result ->
                result == ResetResult.COMPLETED ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.failedFuture(new IllegalStateException("Season reset rejected: " + result)));
    }

    SeasonResetPlan prepareSeasonReset(String id, int rotationTarget, List<Mission> missions, LocalDateTime startedAt) {
        seasonStartDate = startedAt;
        recalculateSeasonEndDate();
        missionCycleStart = startedAt;
        nextMissionReset = startedAt.plusHours(missionIntervalHours());
        return new SeasonResetPlan(id, rotationTarget, plugin.getConfigManager().isResetCoinsOnSeasonEnd(),
                seasonStartDate, seasonEndDate, missionCycleStart, nextMissionReset,
                missionCycleStart.toLocalDate().toString(), missions);
    }

    CompletableFuture<Void> beginSeasonReset(SeasonResetPlan plan) {
        plugin.getDatabaseManager().prepareSeasonReset(plan);
        return plugin.getDatabaseManager().resetSeason();
    }

    void completeSeasonReset(boolean forced) {
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage(
                    forced ? "messages.season.forced-reset" : "messages.season.reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        broadcastNewSeason();

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getPlayerDataManager().loadPlayer(player.getUniqueId());
        }
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
        MissionManager manager = plugin.getMissionManager();
        if (manager == null) throw new IllegalStateException("MissionManager is not ready");
        manager.forceResetMissions();
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
        long hours = Math.max(0, ChronoUnit.HOURS.between(now, nextMissionReset));
        long minutes = Math.max(0, ChronoUnit.MINUTES.between(now, nextMissionReset)) % 60;

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
        long remaining = DailyRewardCooldown.remainingMillis(lastClaimed, dailyRewardClock.getAsLong());
        if (remaining == 0) {
            return plugin.getMessageManager().getMessage("time.available-now");
        }
        long hours = remaining / 3_600_000;
        long minutes = remaining / 60_000 % 60;
        MessageManager messageManager = plugin.getMessageManager();
        return messageManager.getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    public LocalDateTime getNextMissionReset() {
        return nextMissionReset;
    }

    public void setNextMissionReset(LocalDateTime nextMissionReset) {
        restoreMissionSchedule(null, nextMissionReset);
    }

    void restoreMissionSchedule(LocalDateTime cycleStart, LocalDateTime nextReset) {
        nextMissionReset = nextReset;
        // Legacy rows have only a deadline; preserve it once rather than inventing a new countdown.
        missionCycleStart = cycleStart != null ? cycleStart
                : nextReset == null ? null : nextReset.minusHours(missionIntervalHours());
    }

    private int missionIntervalHours() {
        return Math.max(1, plugin.getConfigManager().getMissionResetHours());
    }

    public LocalDateTime getSeasonEndDate() {
        return seasonEndDate;
    }

    public LocalDateTime getSeasonStartDate() {
        return seasonStartDate;
    }
}
