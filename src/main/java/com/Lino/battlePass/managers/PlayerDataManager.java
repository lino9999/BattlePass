package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerDataManager {

    private final BattlePass plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSaves = new ConcurrentHashMap<>();
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor();

    private static final long SAVE_DELAY = 5000L;
    private static final long BATCH_SAVE_INTERVAL = 30000L;

    public PlayerDataManager(BattlePass plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        saveScheduler.scheduleAtFixedRate(this::performBatchSave,
                BATCH_SAVE_INTERVAL, BATCH_SAVE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    public void loadPlayer(UUID uuid) {
        databaseManager.loadPlayerData(uuid).thenAccept(data -> {
            playerCache.put(uuid, data);
            plugin.getLogger().info("Loaded player data for " + uuid + " with " +
                    data.missionProgress.size() + " mission progress entries");
        });
    }

    public PlayerData getPlayerData(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data == null) {
            // Load synchronously if not in cache
            data = databaseManager.loadPlayerData(uuid).join();
            playerCache.put(uuid, data);
        }
        return data;
    }

    public void markForSave(UUID uuid) {
        pendingSaves.put(uuid, System.currentTimeMillis());
    }

    private void performBatchSave() {
        if (pendingSaves.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        Map<UUID, PlayerData> toSave = new HashMap<>();

        pendingSaves.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() >= SAVE_DELAY) {
                PlayerData data = playerCache.get(entry.getKey());
                if (data != null) {
                    toSave.put(entry.getKey(), data);
                }
                return true;
            }
            return false;
        });

        if (toSave.isEmpty()) return;

        // Log saving information
        plugin.getLogger().info("Batch saving " + toSave.size() + " players");

        for (Map.Entry<UUID, PlayerData> entry : toSave.entrySet()) {
            databaseManager.savePlayerData(entry.getKey(), entry.getValue());
        }
    }

    public void saveAllPlayers() {
        plugin.getLogger().info("Saving all " + playerCache.size() + " cached players");

        // Force save all players immediately
        for (Map.Entry<UUID, PlayerData> entry : playerCache.entrySet()) {
            databaseManager.savePlayerData(entry.getKey(), entry.getValue()).join();
        }

        pendingSaves.clear();

        saveScheduler.shutdown();
        try {
            if (!saveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                saveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveScheduler.shutdownNow();
        }
    }

    public void removePlayer(UUID uuid) {
        markForSave(uuid);

        // Save immediately when player leaves
        PlayerData data = playerCache.get(uuid);
        if (data != null) {
            plugin.getLogger().info("Saving player data for " + uuid + " on quit");
            databaseManager.savePlayerData(uuid, data);
            pendingSaves.remove(uuid);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!Bukkit.getOfflinePlayer(uuid).isOnline()) {
                playerCache.remove(uuid);
            }
        }, 200L);
    }

    public void clearCache() {
        playerCache.clear();
        pendingSaves.clear();
    }

    public Map<UUID, PlayerData> getPlayerCache() {
        return playerCache;
    }
}