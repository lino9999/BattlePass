package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerDataManager {

    private final BattlePass plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dirtyVersions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> pendingSaves = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextVersion = new AtomicLong();
    private long cacheGeneration;

    public PlayerDataManager(BattlePass plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public synchronized void loadPlayer(UUID uuid) {
        if (playerCache.containsKey(uuid) || !loadingPlayers.add(uuid)) return;
        long generation = cacheGeneration;
        try {
            databaseManager.loadPlayerData(uuid).whenComplete((data, failure) -> {
                synchronized (PlayerDataManager.this) {
                    if (generation != cacheGeneration) return;
                    loadingPlayers.remove(uuid);
                    if (failure == null && data != null) {
                        playerCache.putIfAbsent(uuid, data);
                    } else {
                        plugin.getLogger().warning("Could not load player data for " + uuid +
                                (failure == null ? "" : ": " + failure.getMessage()));
                    }
                }
            });
        } catch (Exception failure) {
            loadingPlayers.remove(uuid);
            plugin.getLogger().warning("Could not load player data for " + uuid + ": " + failure.getMessage());
        }
    }

    public void removePlayer(UUID uuid) {
        savePlayer(uuid);
    }

    public void savePlayer(UUID uuid) {
        saveSnapshot(uuid);
    }

    private synchronized CompletableFuture<Boolean> saveSnapshot(UUID uuid) {
        CompletableFuture<Boolean> pending = pendingSaves.get(uuid);
        if (pending != null && !pending.isDone()) return pending;
        PlayerData data = playerCache.get(uuid);
        Long version = dirtyVersions.get(uuid);
        if (data == null || version == null) return CompletableFuture.completedFuture(true);

        try {
            CompletableFuture<Boolean> save = databaseManager.savePlayerData(uuid, data.copy())
                    .handle((success, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().severe("Failed to save player " + uuid + ": " + failure.getMessage());
                        }
                        if (Boolean.TRUE.equals(success)) {
                            // Acknowledging this snapshot must not discard later changes.
                            dirtyVersions.remove(uuid, version);
                            return true;
                        }
                        return false;
                    });
            pendingSaves.put(uuid, save);
            save.whenComplete((success, failure) -> pendingSaves.remove(uuid, save));
            return save;
        } catch (Exception failure) {
            plugin.getLogger().severe("Failed to save player " + uuid + ": " + failure.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public void markForSave(UUID uuid) {
        dirtyVersions.put(uuid, nextVersion.incrementAndGet());
    }

    public void saveAllPlayers() {
        for (UUID uuid : dirtyVersions.keySet()) {
            saveSnapshot(uuid);
        }
    }

    public void saveAllPlayersSync() {
        for (UUID uuid : playerCache.keySet()) {
            try {
                CompletableFuture<Boolean> pending = pendingSaves.get(uuid);
                if (pending != null) pending.join();
                // The completed write may have left a newer revision dirty.
                saveSnapshot(uuid).join();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to sync save player " + uuid);
                e.printStackTrace();
            }
        }
    }

    public void clearDirtyPlayers() {
        dirtyVersions.clear();
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerCache.get(uuid);
    }

    public Map<UUID, PlayerData> getPlayerCache() {
        return playerCache;
    }

    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    public void clearCache(boolean save) {
        if (save) {
            saveAllPlayersSync();
        }
        // Never hold the cache monitor while waiting for work on the shared database executor:
        // a load completing on that executor also needs this monitor before it can finish.
        synchronized (this) {
            if (save && dirtyVersions.keySet().stream().anyMatch(playerCache::containsKey)) {
                throw new IllegalStateException("Cannot clear the player cache while player changes remain unsaved");
            }
            if (!save) clearDirtyPlayers();
            cacheGeneration++;
            loadingPlayers.clear();
            playerCache.clear();
        }
    }

    public void clearCache() {
        clearCache(true);
    }

    public void cleanupStaleEntries() {
        playerCache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null && !dirtyVersions.containsKey(uuid)
                && !pendingSaves.containsKey(uuid));

        for (UUID uuid : playerCache.keySet()) {
            if (Bukkit.getPlayer(uuid) == null && dirtyVersions.containsKey(uuid)) {
                savePlayer(uuid);
            }
        }

        dirtyVersions.keySet().removeIf(uuid -> !playerCache.containsKey(uuid));
    }
}
