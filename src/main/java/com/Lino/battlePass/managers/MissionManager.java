package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerDataManager playerDataManager;

    private final MissionGenerator missionGenerator;
    private final MissionProgressTracker progressTracker;
    private final MissionResetHandler resetHandler;

    private volatile List<Mission> dailyMissions = Collections.synchronizedList(new ArrayList<>());
    private String currentMissionDate;

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
        databaseManager.loadSeasonData().thenAccept(data -> {
            if (data.containsKey("endDate")) {
                resetHandler.setSeasonEndDate((LocalDateTime) data.get("endDate"));
                if (data.containsKey("missionResetTime")) {
                    resetHandler.setNextMissionReset((LocalDateTime) data.get("missionResetTime"));
                }
                if (data.containsKey("currentMissionDate")) {
                    currentMissionDate = (String) data.get("currentMissionDate");
                }

                if (LocalDateTime.now().isAfter(resetHandler.getSeasonEndDate())) {
                    Bukkit.getScheduler().runTask(plugin, () -> resetSeason());
                    return;
                }
            } else {
                resetHandler.calculateSeasonEndDate();
                currentMissionDate = LocalDateTime.now().toLocalDate().toString();
                resetHandler.calculateNextReset();
                saveSeasonData();
            }

            loadMissionsAfterSeasonData();
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load season data: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

    private void loadMissionsAfterSeasonData() {
        databaseManager.loadDailyMissions().thenAccept(missions -> {
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
                    progressTracker.resetProgress(currentMissionDate);
                }

                generateDailyMissions();
                resetHandler.calculateNextReset();
                saveDailyMissions();
                saveSeasonData();
            } else {
                dailyMissions = new ArrayList<>(missions);

                if (resetHandler.getNextMissionReset() == null) {
                    resetHandler.calculateNextReset();
                    saveSeasonData();
                }
            }
        });
    }

    public void recalculateResetTimeOnReload() {
        resetHandler.recalculateResetTimeOnReload();
        saveSeasonData();
    }

    private void generateDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }

        dailyMissions = new ArrayList<>(missionGenerator.generateDailyMissions(currentMissionDate));
    }

    public void checkMissionReset() {
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

            progressTracker.resetProgress(currentMissionDate);
            databaseManager.clearOldMissionProgress(currentMissionDate);
        }
    }

    public void checkSeasonReset() {
        if (resetHandler.shouldResetSeason()) {
            resetSeason();
        }
    }

    private void resetSeason() {
        resetHandler.resetSeason();
        currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        generateDailyMissions();
        resetHandler.calculateNextReset();
        saveSeasonData();
        progressTracker.resetProgress(currentMissionDate);
    }

    public void forceResetSeason() {
        resetHandler.forceResetSeason();
        currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        generateDailyMissions();
        saveDailyMissions();
        saveSeasonData();
        progressTracker.resetProgress(currentMissionDate);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getCoinsDistributionTask() != null) {
                plugin.getCoinsDistributionTask().cancel();
                CoinsDistributionTask newTask = new CoinsDistributionTask(plugin);
                plugin.setCoinsDistributionTask(newTask);
                newTask.runTaskTimer(plugin, 200L, 1200L);
            }
        }, 40L);
    }

    public void forceResetMissions() {
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

        progressTracker.resetProgress(currentMissionDate);
        databaseManager.clearOldMissionProgress(currentMissionDate);
    }

    public void progressMission(Player player, String type, String target, int amount) {
        progressTracker.trackProgress(player, type, target, amount, dailyMissions);
    }

    public void clearPlayerActionbars(UUID uuid) {
        progressTracker.clearPlayerActionbars(uuid);
    }

    public int getCompletedMissionsCount(PlayerData data) {
        return progressTracker.getCompletedMissionsCount(data, dailyMissions);
    }

    private void saveSeasonData() {
        databaseManager.saveSeasonData(
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
        saveSeasonData();
        saveDailyMissions();
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
        return currentMissionDate != null && !dailyMissions.isEmpty();
    }
}
