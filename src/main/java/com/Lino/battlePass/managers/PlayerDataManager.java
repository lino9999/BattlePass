package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data != null && dirtyPlayers.contains(uuid)) {
            PlayerData snapshot = data.copy();

            databaseManager.savePlayerData(uuid, snapshot).thenAccept(success -> {
                if (success) {
                    dirtyPlayers.remove(uuid);
                }
            });
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

        for (UUID uuid : playersToSave) {
            databaseManager.savePlayerData(uuid, snapshots.get(uuid)).thenAccept(success -> {
                if (success) {
                    dirtyPlayers.remove(uuid);
                }
            });
        }
    }

    public void saveAllPlayersSync() {
        Set<UUID> playersToSave = playerCache.keySet().stream()
                .filter(dirtyPlayers::contains)
                .collect(Collectors.toSet());

        if (playersToSave.isEmpty()) return;

        Map<UUID, PlayerData> snapshots = playersToSave.stream()
                .collect(Collectors.toMap(uuid -> uuid, uuid -> playerCache.get(uuid).copy()));

        for (UUID uuid : playersToSave) {
            try {
                boolean success = databaseManager.savePlayerData(uuid, snapshots.get(uuid)).join();
                if (success) {
                    dirtyPlayers.remove(uuid);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to sync save player " + uuid);
                e.printStackTrace();
            }
        }
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

    public void clearCache(boolean save) {
        if (save) {
            saveAllPlayersSync();
        } else {
            clearDirtyPlayers();
        }
        playerCache.clear();
    }

    public void clearCache() {
        clearCache(true);
    }

    public void cleanupStaleEntries() {
        playerCache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null && !dirtyPlayers.contains(uuid));

        for (UUID uuid : playerCache.keySet()) {
            if (Bukkit.getPlayer(uuid) == null && dirtyPlayers.contains(uuid)) {
                savePlayer(uuid);
            }
        }

        dirtyPlayers.removeIf(uuid -> !playerCache.containsKey(uuid));
    }
}