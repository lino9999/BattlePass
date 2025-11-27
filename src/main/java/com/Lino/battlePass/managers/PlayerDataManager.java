package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerDataManager {

    private final BattlePass plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public PlayerDataManager(BattlePass plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadPlayer(UUID uuid) {
        if (playerCache.containsKey(uuid)) return;
        databaseManager.loadPlayerData(uuid).thenAccept(data -> {
            if (data != null) {
                playerCache.put(uuid, data);
            } else {
                plugin.getLogger().warning("Could not load player data for " + uuid + ". The player might not be able to interact with BattlePass features until they rejoin.");
            }
        });
    }

    public void removePlayer(UUID uuid) {
        savePlayer(uuid);
        playerCache.remove(uuid);
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data != null && dirtyPlayers.contains(uuid)) {
            PlayerData snapshot = data.copy();

            databaseManager.savePlayerData(uuid, snapshot).join();
            dirtyPlayers.remove(uuid);
        }
    }

    public void markForSave(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    public void saveAllPlayers() {
        Set<UUID> playersToSave = playerCache.keySet().stream()
                .filter(dirtyPlayers::contains)
                .collect(Collectors.toSet());

        if (playersToSave.isEmpty()) return;

        Map<UUID, PlayerData> snapshots = playersToSave.stream()
                .collect(Collectors.toMap(uuid -> uuid, uuid -> playerCache.get(uuid).copy()));

        CompletableFuture<?>[] futures = playersToSave.stream()
                .map(uuid -> databaseManager.savePlayerData(uuid, snapshots.get(uuid)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).thenRun(() -> {
            dirtyPlayers.removeAll(playersToSave);
        });
    }

    public void saveAllPlayersSync() {
        Set<UUID> playersToSave = playerCache.keySet().stream()
                .filter(dirtyPlayers::contains)
                .collect(Collectors.toSet());

        if (playersToSave.isEmpty()) return;

        Map<UUID, PlayerData> snapshots = playersToSave.stream()
                .collect(Collectors.toMap(uuid -> uuid, uuid -> playerCache.get(uuid).copy()));

        CompletableFuture<?>[] futures = playersToSave.stream()
                .map(uuid -> databaseManager.savePlayerData(uuid, snapshots.get(uuid)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
        dirtyPlayers.removeAll(playersToSave);
    }

    public void clearDirtyPlayers() {
        dirtyPlayers.clear();
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

    public void clearCache() {
        saveAllPlayers();
        playerCache.clear();
    }

    public void cleanupStaleEntries() {
        playerCache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        dirtyPlayers.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }
}