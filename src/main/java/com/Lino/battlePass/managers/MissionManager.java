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
    private boolean seasonResetInProgress;

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
            plugin.getLogger().severe("Failed to load season data: " + ex.getMessage());
            ex.printStackTrace();
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
                if (resetHandler.getNextMissionReset() != null && now.isAfter(resetHandler.getNextMissionReset())) {
                    currentMissionDate = now.toLocalDate().toString();
                    databaseManager.clearOldMissionProgress(currentMissionDate);
                    progressTracker.resetProgress();
                }

                generateDailyMissions();
                resetHandler.calculateNextReset();
                saveDailyMissions();
                saveSeasonData();
            } else {
                dailyMissions = new ArrayList<>(missions);
                missionsLoadAttempted = true;

                if (resetHandler.getNextMissionReset() == null) {
                    resetHandler.calculateNextReset();
                    saveSeasonData();
                }
            }
        }));
    }

    public void recalculateResetTimeOnReload() {
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
        if (seasonResetInProgress) return;
        if (resetHandler.shouldResetMissions()) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();

            generateDailyMissions();
            resetHandler.calculateNextReset();
            saveDailyMissions();
            saveSeasonData();

            MessageManager messageManager = plugin.getMessageManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.reset"));
            }

            progressTracker.resetProgress();
            databaseManager.clearOldMissionProgress(currentMissionDate);
        }
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
        if (seasonResetInProgress) return;
        seasonResetInProgress = true;
        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        if (rotation != null && rotation.isRotationEnabled()) {
            rotation.rotateToNextSeason();
            plugin.getConfigManager().reload();
            plugin.getRewardManager().loadRewards();
        }

        CompletableFuture<Void> reset = forced ? resetHandler.forceResetSeason() : resetHandler.resetSeason();
        currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        generateDailyMissions();
        saveSeasonData();
        progressTracker.resetProgress();
        // resetSeason deletes daily_missions; save the new set only after that deletion completes.
        reset.thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            saveDailyMissions();
            seasonResetInProgress = false;
            if (forced) plugin.startCoinsDistributionTask(null);
        })).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to reset season: " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> seasonResetInProgress = false);
            return null;
        });
    }

    private volatile long lastForceSeasonReset = 0;

    public void forceResetSeason() {
        long now = System.currentTimeMillis();
        if (seasonResetInProgress || now - lastForceSeasonReset < 5000) return;
        lastForceSeasonReset = now;
        resetSeason(true);
    }

    public void forceResetMissions() {
        if (seasonResetInProgress) return;
        currentMissionDate = LocalDateTime.now().toLocalDate().toString();

        generateDailyMissions();
        resetHandler.calculateNextReset();
        saveDailyMissions();
        saveSeasonData();

        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.forced-reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        progressTracker.resetProgress();
        databaseManager.clearOldMissionProgress(LocalDateTime.now().plusDays(1).toLocalDate().toString());
    }

    public void progressMission(Player player, String type, String target, int amount) {
        if (amount <= 0) return;
        if (configManager.isMissionWorldDisabled(player.getWorld().getName())) return;
        progressTracker.trackProgress(player, type, target, amount, dailyMissions);
    }

    public void progressMission(Player player, String type, Collection<String> targets, int amount) {
        if (amount <= 0) return;
        if (configManager.isMissionWorldDisabled(player.getWorld().getName())) return;
        progressTracker.trackProgress(player, type, targets, amount, dailyMissions);
    }

    public void clearPlayerActionbars(UUID uuid) {
        progressTracker.clearPlayerActionbars(uuid);
    }

    public int getCompletedMissionsCount(PlayerData data) {
        return progressTracker.getCompletedMissionsCount(data, dailyMissions);
    }

    private void saveSeasonData() {
        if (resetHandler.getSeasonStartDate() == null || resetHandler.getSeasonEndDate() == null) return;
        databaseManager.saveSeasonData(
                resetHandler.getSeasonStartDate(),
                resetHandler.getSeasonEndDate(),
                resetHandler.getNextMissionReset(),
                currentMissionDate
        );
    }

    private void saveDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }
        databaseManager.saveDailyMissions(dailyMissions, currentMissionDate);
    }

    public void shutdown() {
        if (missionsLoadAttempted) {
            saveSeasonData();
            saveDailyMissions();
        }
        progressTracker.shutdown();
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
        return currentMissionDate != null && missionsLoadAttempted && !seasonResetInProgress;
    }
}
