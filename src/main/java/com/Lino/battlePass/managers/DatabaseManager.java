package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Stable entry point for database operations, delegated by persistence responsibility. */
public class DatabaseManager {

    private final DatabaseConnections connections;
    private final SeasonDataRepository seasonData;
    private final PlayerDataRepository players;
    private final DailyMissionRepository missions;

    public DatabaseManager(BattlePass plugin) {
        connections = new DatabaseConnections(plugin);
        seasonData = new SeasonDataRepository(plugin, connections);
        players = new PlayerDataRepository(plugin, connections, this::getCurrentMissionDate);
        missions = new DailyMissionRepository(connections, this::getCurrentMissionDate);
    }

    public CompletableFuture<Void> initialize() {
        return connections.initialize();
    }

    public synchronized CompletableFuture<Void> saveSeasonData(LocalDateTime startDate, LocalDateTime endDate,
                                                               LocalDateTime missionResetTime, String currentMissionDate) {
        return seasonData.saveSeasonData(startDate, endDate, missionResetTime, currentMissionDate);
    }

    public CompletableFuture<Void> saveCoinsDistributionTime(LocalDateTime nextDistribution) {
        return seasonData.saveCoinsDistributionTime(nextDistribution);
    }

    public CompletableFuture<LocalDateTime> loadCoinsDistributionTime() {
        return seasonData.loadCoinsDistributionTime();
    }

    public CompletableFuture<Map<String, Object>> loadSeasonData() {
        return seasonData.loadSeasonData();
    }

    public String getCurrentMissionDate() {
        return seasonData.getCurrentMissionDate();
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return players.loadPlayerData(uuid);
    }

    public CompletableFuture<Map<String, Set<Integer>>> getClaimedRewards(UUID uuid) {
        return players.getClaimedRewards(uuid);
    }

    public CompletableFuture<Boolean> savePlayerData(UUID uuid, PlayerData data) {
        return players.savePlayerData(uuid, data);
    }

    public CompletableFuture<Void> updatePlayerCoins(UUID uuid, int amount) {
        return players.updatePlayerCoins(uuid, amount);
    }

    public CompletableFuture<List<PlayerData>> getTop10Players() {
        return players.getTop10Players();
    }

    public CompletableFuture<Void> resetSeason() {
        return players.resetSeason();
    }

    public CompletableFuture<Void> saveDailyMissions(List<Mission> missions, String missionDate) {
        return this.missions.saveDailyMissions(missions, missionDate);
    }

    public CompletableFuture<List<Mission>> loadDailyMissions() {
        return missions.loadDailyMissions();
    }

    public CompletableFuture<Void> clearOldMissionProgress(String currentDate) {
        return missions.clearOldMissionProgress(currentDate);
    }

    public void shutdown() {
        seasonData.awaitPendingSaves();
        connections.shutdown();
    }
}
