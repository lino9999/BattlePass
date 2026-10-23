package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MissionManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerDataManager playerDataManager;

    private final MissionGenerator missionGenerator;
    private final MissionProgressTracker progressTracker;
    private final MissionResetHandler resetHandler;

    private volatile List<Mission> dailyMissions = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean missionsLoadAttempted = false;
    private String currentMissionDate;
    private volatile boolean seasonResetInProgress;
    private volatile boolean shuttingDown;
    private volatile boolean persistenceFailed;
    private volatile boolean dailyResetInProgress;
    private boolean calendarReloadPending;
    private CompletableFuture<Void> pendingDailyReset;
    private Runnable pendingDailyCommit;
    private CompletableFuture<Void> pendingSeasonReset;
    private Runnable pendingSeasonCommit;

    public MissionManager(BattlePass plugin, ConfigManager configManager, DatabaseManager databaseManager, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerDataManager = playerDataManager;

        this.missionGenerator = new MissionGenerator(configManager);
        this.progressTracker = new MissionProgressTracker(plugin);
        this.resetHandler = new MissionResetHandler(plugin);
    }

    public void initialize() {
        databaseManager.loadSeasonData().thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (data.containsKey("endDate")) {
                resetHandler.restoreSeasonDates((LocalDateTime) data.get("startDate"), (LocalDateTime) data.get("endDate"));
                if (data.containsKey("missionResetTime")) {
                    resetHandler.setNextMissionReset((LocalDateTime) data.get("missionResetTime"));
                }
                if (data.containsKey("currentMissionDate")) {
                    currentMissionDate = (String) data.get("currentMissionDate");
                }

                if (resetHandler.shouldResetSeason()) {
                    resetSeason();
                    return;
                }
            } else {
                resetHandler.calculateSeasonEndDate();
                currentMissionDate = LocalDateTime.now().toLocalDate().toString();
                resetHandler.calculateNextReset();
            }

            saveSeasonData();
            loadMissionsAfterSeasonData();
        })).exceptionally(ex -> {
            persistenceFailure("load season data", ex);
            return null;
        });
    }

    private void loadMissionsAfterSeasonData() {
        databaseManager.loadDailyMissions().thenAccept(missions -> Bukkit.getScheduler().runTask(plugin, () -> {
            LocalDateTime now = LocalDateTime.now();

            if (currentMissionDate == null) {
                currentMissionDate = now.toLocalDate().toString();
            }

            boolean needNewMissions = missions.isEmpty() ||
                    (resetHandler.getNextMissionReset() != null && now.isAfter(resetHandler.getNextMissionReset()));

            if (needNewMissions) {
                resetDailyMissions(false, false);
            } else {
                dailyMissions = new ArrayList<>(missions);
                missionsLoadAttempted = true;

                if (resetHandler.getNextMissionReset() == null) {
                    resetHandler.calculateNextReset();
                    saveSeasonData();
                }
            }
        })).exceptionally(ex -> {
            persistenceFailure("load daily missions", ex);
            return null;
        });
    }

    public void recalculateResetTimeOnReload() {
        if (dailyResetInProgress) {
            calendarReloadPending = true;
            return;
        }
        resetHandler.recalculateResetTimeOnReload();
        resetHandler.recalculateSeasonEndDate();
        saveSeasonData();
    }

    private void generateDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }

        dailyMissions = new ArrayList<>(missionGenerator.generateDailyMissions());
        missionsLoadAttempted = true;
    }

    public void checkMissionReset() {
        if (isProgressPaused() || resetHandler.shouldResetSeason()) return;
        if (resetHandler.shouldResetMissions()) resetDailyMissions(false, true);
    }

    public void checkSeasonReset() {
        if (resetHandler.shouldResetSeason()) {
            resetSeason();
        }
    }

    private void resetSeason() {
        resetSeason(false);
    }

    private void resetSeason(boolean forced) {
        if (isProgressPaused() || persistenceFailed) return;
        try {
            // Flush before rotating configuration or publishing a new season deadline.
            playerDataManager.clearCache(true);
        } catch (IllegalStateException failure) {
            plugin.getLogger().severe("Season reset postponed to preserve unsaved player data: " + failure.getMessage());
            return;
        }
        seasonResetInProgress = true;
        AtomicBoolean committed = new AtomicBoolean();
        pendingSeasonCommit = () -> {
            if (!committed.compareAndSet(false, true)) return;
            try {
                SeasonRotationManager rotation = plugin.getSeasonRotationManager();
                if (rotation != null && rotation.isRotationEnabled()) {
                    rotation.rotateToNextSeason();
                    plugin.getConfigManager().reload();
                    plugin.getRewardManager().loadRewards();
                }
                seasonResetInProgress = false;
                saveDailyMissions();
                if (!shuttingDown && !persistenceFailed) {
                    resetHandler.completeSeasonReset(forced);
                    if (forced) plugin.startCoinsDistributionTask(null);
                }
                pendingSeasonReset = null;
                pendingSeasonCommit = null;
            } catch (Exception failure) {
                persistenceFailure("finish the season reset", failure);
            }
        };
        Runnable commit = pendingSeasonCommit;
        try {
            CompletableFuture<Void> reset = resetHandler.beginSeasonReset();
            pendingSeasonReset = reset;
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
            generateDailyMissions();
            saveSeasonData();
            progressTracker.resetProgress();
            reset.thenRun(() -> {
                if (!shuttingDown) Bukkit.getScheduler().runTask(plugin, commit);
            }).exceptionally(ex -> {
                persistenceFailure("reset season data", ex);
                return null;
            });
        } catch (Exception failure) {
            persistenceFailure("start the season reset", failure);
        }
    }

    private volatile long lastForceSeasonReset = 0;

    public void forceResetSeason() {
        long now = System.currentTimeMillis();
        if (isProgressPaused() || now - lastForceSeasonReset < 5000) return;
        lastForceSeasonReset = now;
        resetSeason(true);
    }

    public void forceResetMissions() {
        resetDailyMissions(true, true);
    }

    private void resetDailyMissions(boolean forced, boolean notify) {
        if (isProgressPaused() || persistenceFailed) return;
        List<Mission> replacements;
        LocalDateTime nextReset;
        String missionDate = LocalDateTime.now().toLocalDate().toString();
        try {
            replacements = new ArrayList<>(missionGenerator.generateDailyMissions());
            nextReset = LocalDateTime.now().plusHours(configManager.getMissionResetHours());
            // Drain old snapshots and invalidate old loads before submitting the cycle replacement.
            playerDataManager.clearCache(true);
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("Daily mission reset postponed to preserve player data: " + failure.getMessage());
            return;
        }
        dailyResetInProgress = true;
        AtomicBoolean committed = new AtomicBoolean();
        pendingDailyCommit = () -> {
            if (!committed.compareAndSet(false, true)) return;
            try {
                dailyMissions = new ArrayList<>(replacements);
                currentMissionDate = missionDate;
                resetHandler.setNextMissionReset(nextReset);
                missionsLoadAttempted = true;
                progressTracker.resetProgress();
                dailyResetInProgress = false;
                pendingDailyReset = null;
                pendingDailyCommit = null;
                if (calendarReloadPending) {
                    calendarReloadPending = false;
                    recalculateResetTimeOnReload();
                }
                if (!shuttingDown && !persistenceFailed) {
                    playerDataManager.loadOnlinePlayers();
                    if (notify) {
                        MessageManager messages = plugin.getMessageManager();
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendMessage(messages.getPrefix() + messages.getMessage(
                                    forced ? "messages.mission.forced-reset" : "messages.mission.reset"));
                            if (forced) player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                    }
                }
            } catch (RuntimeException failure) {
                persistenceFailure("finish the daily mission reset", failure);
            }
        };
        Runnable commit = pendingDailyCommit;
        try {
            pendingDailyReset = databaseManager.resetDailyMissions(replacements, missionDate,
                    resetHandler.getSeasonStartDate(), resetHandler.getSeasonEndDate(), nextReset);
            pendingDailyReset.whenComplete((ignored, failure) -> {
                if (failure != null) persistenceFailure("reset daily missions", failure);
                else if (!shuttingDown) {
                    try {
                        Bukkit.getScheduler().runTask(plugin, commit);
                    } catch (RuntimeException schedulingFailure) {
                        if (!shuttingDown) persistenceFailure("schedule the daily reset completion", schedulingFailure);
                    }
                }
            });
        } catch (RuntimeException failure) {
            persistenceFailure("start the daily mission reset", failure);
        }
    }

    public void progressMission(Player player, String type, String target, int amount) {
        if (amount <= 0 || isProgressPaused()) return;
        if (configManager.isMissionWorldDisabled(player.getWorld().getName())) return;
        progressTracker.trackProgress(player, type, target, amount, dailyMissions);
    }

    public void progressMission(Player player, String type, Collection<String> targets, int amount) {
        if (amount <= 0 || isProgressPaused()) return;
        if (configManager.isMissionWorldDisabled(player.getWorld().getName())) return;
        progressTracker.trackProgress(player, type, targets, amount, dailyMissions);
    }

    public void clearPlayerActionbars(UUID uuid) {
        progressTracker.clearPlayerActionbars(uuid);
    }

    public boolean isProgressPaused() {
        return seasonResetInProgress || dailyResetInProgress || shuttingDown;
    }

    public int getCompletedMissionsCount(PlayerData data) {
        return progressTracker.getCompletedMissionsCount(data, dailyMissions);
    }

    private void saveSeasonData() {
        if (resetHandler.getSeasonStartDate() == null || resetHandler.getSeasonEndDate() == null) return;
        observeWrite(databaseManager.saveSeasonData(
                resetHandler.getSeasonStartDate(),
                resetHandler.getSeasonEndDate(),
                resetHandler.getNextMissionReset(),
                currentMissionDate
        ), "save season dates");
    }

    private void saveDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }
        observeWrite(databaseManager.saveDailyMissions(dailyMissions, currentMissionDate), "save daily missions");
    }

    private void observeWrite(CompletableFuture<?> operation, String description) {
        if (operation != null) operation.whenComplete((result, failure) -> {
            if (failure != null) persistenceFailure(description, failure);
        });
    }

    private synchronized void persistenceFailure(String operation, Throwable failure) {
        if (persistenceFailed) return;
        persistenceFailed = true;
        seasonResetInProgress = true;
        plugin.getLogger().severe("BattlePass cannot " + operation + ": " + failure.getMessage() +
                ". Disabling the plugin to preserve persistent data.");
        if (!shuttingDown) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!shuttingDown) Bukkit.getPluginManager().disablePlugin(plugin);
        });
    }

    public void shutdown() {
        shuttingDown = true;
        finishPendingReset(pendingDailyReset, pendingDailyCommit, "finish a pending daily reset during shutdown");
        CompletableFuture<Void> reset = pendingSeasonReset;
        Runnable commit = pendingSeasonCommit;
        finishPendingReset(reset, commit, "finish a pending season reset during shutdown");
        if (missionsLoadAttempted && !persistenceFailed) {
            saveSeasonData();
            saveDailyMissions();
        }
        progressTracker.shutdown();
    }

    private void finishPendingReset(CompletableFuture<Void> reset, Runnable commit, String description) {
        if (reset != null && commit != null && !persistenceFailed) {
            try {
                // Finish a committed reset directly on shutdown's main thread, not through a cancelled task.
                reset.get(30, TimeUnit.SECONDS);
                commit.run();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                persistenceFailure(description, failure);
            } catch (Exception failure) {
                persistenceFailure(description, failure);
            }
        }
    }

    public String getTimeUntilReset() {
        return resetHandler.getTimeUntilReset();
    }

    public String getTimeUntilSeasonEnd() {
        return resetHandler.getTimeUntilSeasonEnd();
    }

    public String getTimeUntilDailyReward(long lastClaimed) {
        return resetHandler.getTimeUntilDailyReward(lastClaimed);
    }

    public List<Mission> getDailyMissions() {
        return new ArrayList<>(dailyMissions);
    }

    public String getCurrentMissionDate() {
        return currentMissionDate;
    }

    public boolean isInitialized() {
        return currentMissionDate != null && missionsLoadAttempted && !isProgressPaused() && !persistenceFailed;
    }
}
