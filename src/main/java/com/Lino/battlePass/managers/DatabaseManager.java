package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Server;

/** Stable entry point for database operations, delegated by persistence responsibility. */
public class DatabaseManager {

    private final BattlePass plugin;
    private final DatabaseWriteQueue operations;
    private final AtomicBoolean failureReported = new AtomicBoolean();
    private volatile boolean shuttingDown;
    private final DatabaseConnections connections;
    private final SeasonDataRepository seasonData;
    private final PlayerDataRepository players;
    private final DailyMissionRepository missions;
    private final MissionCycleRepository missionCycles;
    private final CoinDistributionRepository coinDistributions;

    public DatabaseManager(BattlePass plugin) {
        this.plugin = plugin;
        operations = new DatabaseWriteQueue(this::criticalFailure);
        connections = new DatabaseConnections(plugin);
        seasonData = new SeasonDataRepository(plugin, connections);
        players = new PlayerDataRepository(plugin, connections, this::getCurrentMissionDate);
        missions = new DailyMissionRepository(connections, this::getCurrentMissionDate);
        missionCycles = new MissionCycleRepository(connections, missions, seasonData);
        coinDistributions = new CoinDistributionRepository(connections, seasonData);
    }

    public CompletableFuture<Void> initialize() {
        return operations.write(connections::initialize);
    }

    public synchronized CompletableFuture<Void> saveSeasonData(LocalDateTime startDate, LocalDateTime endDate,
                                                               LocalDateTime missionResetTime, String currentMissionDate) {
        LocalDateTime cycleStart = missionCycleStart(missionResetTime);
        return operations.write(() -> seasonData.saveSeasonData(startDate, endDate, missionResetTime, currentMissionDate, cycleStart));
    }

    public CompletableFuture<Void> saveCoinsDistributionTime(LocalDateTime nextDistribution) {
        return operations.write(() -> seasonData.saveCoinsDistributionTime(nextDistribution));
    }

    /** Completes the short commit phase before cached balances become spendable. Never hold the cache monitor here. */
    public void commitCoinsDistribution(LocalDateTime expectedDeadline, LocalDateTime nextDeadline, Map<UUID, Integer> credits) {
        if (expectedDeadline == null || nextDeadline == null || !nextDeadline.isAfter(expectedDeadline)) {
            throw new IllegalArgumentException("A coin delivery must advance its expected deadline");
        }
        Map<UUID, Integer> snapshot = new LinkedHashMap<>(credits);
        snapshot.forEach((uuid, amount) -> { Objects.requireNonNull(uuid); Objects.requireNonNull(amount); });
        CoinDistributionRepository.Result result = operations.write(() ->
                coinDistributions.distribute(expectedDeadline, nextDeadline, snapshot)).join();
        if (!result.applied()) throw new CoinsDeadlineChangedException(result.nextDeadline());
        credits.clear();
        credits.putAll(result.credits());
    }

    public static final class CoinsDeadlineChangedException extends IllegalStateException {
        private final LocalDateTime nextDeadline;

        CoinsDeadlineChangedException(LocalDateTime nextDeadline) {
            super("The coin delivery deadline changed before commit");
            this.nextDeadline = nextDeadline;
        }

        public LocalDateTime getNextDeadline() { return nextDeadline; }
    }

    public CompletableFuture<LocalDateTime> loadCoinsDistributionTime() {
        return operations.read(seasonData::loadCoinsDistributionTime);
    }

    public CompletableFuture<Map<String, Object>> loadSeasonData() {
        return operations.read(seasonData::loadSeasonData);
    }

    public String getCurrentMissionDate() {
        return seasonData.getCurrentMissionDate();
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return operations.read(() -> players.loadPlayerData(uuid));
    }

    public CompletableFuture<Map<String, Set<Integer>>> getClaimedRewards(UUID uuid) {
        return operations.read(() -> players.getClaimedRewards(uuid));
    }

    public CompletableFuture<Boolean> savePlayerData(UUID uuid, PlayerData data) {
        PlayerData snapshot = data.copy();
        return operations.write(() -> players.savePlayerData(uuid, snapshot));
    }

    public CompletableFuture<Void> updatePlayerCoins(UUID uuid, int amount) {
        return operations.write(() -> players.updatePlayerCoins(uuid, amount));
    }

    public CompletableFuture<List<PlayerData>> getTop10Players() {
        return operations.read(players::getTop10Players);
    }

    public CompletableFuture<Void> resetSeason() {
        return operations.write(players::resetSeason);
    }

    public CompletableFuture<Void> saveDailyMissions(List<Mission> missions, String missionDate) {
        List<Mission> snapshot = snapshotMissions(missions);
        return operations.write(() -> this.missions.saveDailyMissions(snapshot, missionDate));
    }

    public CompletableFuture<Void> resetDailyMissions(List<Mission> missions, String missionDate, LocalDateTime startDate,
                                                     LocalDateTime endDate, LocalDateTime nextReset) {
        List<Mission> snapshot = snapshotMissions(missions);
        LocalDateTime cycleStart = missionCycleStart(nextReset);
        return operations.write(() -> missionCycles.replace(snapshot, missionDate, startDate, endDate, nextReset, cycleStart));
    }

    private LocalDateTime missionCycleStart(LocalDateTime nextReset) {
        // Capture config at submission, before a queued write can outlive a reload.
        return nextReset == null ? null : nextReset.minusHours(Math.max(1, plugin.getConfigManager().getMissionResetHours()));
    }

    private List<Mission> snapshotMissions(List<Mission> missions) {
        return missions.stream().map(mission -> new Mission(mission.name, mission.type, mission.target,
                new ArrayList<>(mission.additionalTargets), mission.required, mission.xpReward)).toList();
    }

    public CompletableFuture<List<Mission>> loadDailyMissions() {
        return operations.read(missions::loadDailyMissions);
    }

    public CompletableFuture<Void> clearOldMissionProgress(String currentDate) {
        return operations.write(() -> missions.clearOldMissionProgress(currentDate));
    }

    public void shutdown() {
        shuttingDown = true;
        try {
            operations.closeAndAwait();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            plugin.getLogger().warning("Could not finish pending database operations: " + failure.getMessage());
        }
        seasonData.awaitPendingSaves();
        connections.shutdown();
    }

    private void criticalFailure(Throwable failure) {
        if (shuttingDown || !failureReported.compareAndSet(false, true)) return;
        plugin.getLogger().severe("A critical database write failed; later database operations have been stopped: " + failure.getMessage());
        Server server = plugin.getServer();
        if (server != null && plugin.isEnabled()) {
            server.getScheduler().runTask(plugin, () -> server.getPluginManager().disablePlugin(plugin));
        }
    }
}
