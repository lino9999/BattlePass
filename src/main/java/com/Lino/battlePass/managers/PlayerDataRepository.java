package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/** Persists player state, claimed rewards and player mission progress. */
final class PlayerDataRepository {

    private final BattlePass plugin;
    private final DatabaseConnections connections;
    private final Supplier<String> currentMissionDate;

    PlayerDataRepository(BattlePass plugin, DatabaseConnections connections, Supplier<String> currentMissionDate) {
        this.plugin = plugin;
        this.connections = connections;
        this.currentMissionDate = currentMissionDate;
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = new PlayerData(uuid);
            String sqlSelect = "SELECT * FROM " + connections.getPrefix() + "players WHERE uuid = ?";
            String sqlInsert = connections.isMySQL()
                    ? "INSERT IGNORE INTO " + connections.getPrefix() + "players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels, has_premium, last_daily_reward, battle_coins, exclude_from_top) VALUES (?, 0, 1, '', '', 0, 0, 0, 0, 0, 0)"
                    : "INSERT OR IGNORE INTO " + connections.getPrefix() + "players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels, has_premium, last_daily_reward, battle_coins, exclude_from_top) VALUES (?, 0, 1, '', '', 0, 0, 0, 0, 0, 0)";

            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();

                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            data.xp = rs.getInt("xp");
                            data.level = rs.getInt("level");
                            data.lastNotification = rs.getInt("last_notification");
                            data.totalLevels = rs.getInt("total_levels");
                            data.hasPremium = rs.getInt("has_premium") == 1;
                            data.lastDailyReward = rs.getLong("last_daily_reward");
                            data.battleCoins = rs.getInt("battle_coins");
                            data.excludeFromTop = rs.getInt("exclude_from_top") == 1;

                            String claimedFree = rs.getString("claimed_free");
                            if (claimedFree != null && !claimedFree.isEmpty()) {
                                for (String level : claimedFree.split(",")) {
                                    if (!level.isEmpty()) data.claimedFreeRewards.add(Integer.parseInt(level));
                                }
                            }

                            String claimedPremium = rs.getString("claimed_premium");
                            if (claimedPremium != null && !claimedPremium.isEmpty()) {
                                for (String level : claimedPremium.split(",")) {
                                    if (!level.isEmpty()) data.claimedPremiumRewards.add(Integer.parseInt(level));
                                }
                            }
                        }
                    }
                }

                String missionDate = currentMissionDate.get();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + connections.getPrefix() + "missions WHERE uuid = ? AND date = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, missionDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            data.missionProgress.put(rs.getString("mission"), Math.max(0, rs.getInt("progress")));
                        }
                    }
                }

                return data;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player data: " + e.getMessage());
                e.printStackTrace();
                return null;
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
    }

    public CompletableFuture<Map<String, Set<Integer>>> getClaimedRewards(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Set<Integer>> claimedData = new HashMap<>();
            claimedData.put("free", new HashSet<>());
            claimedData.put("premium", new HashSet<>());

            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT claimed_free, claimed_premium FROM " + connections.getPrefix() + "players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String claimedFreeStr = rs.getString("claimed_free");
                            if (claimedFreeStr != null && !claimedFreeStr.isEmpty()) {
                                Arrays.stream(claimedFreeStr.split(",")).filter(s -> !s.isEmpty()).map(Integer::parseInt).forEach(claimedData.get("free")::add);
                            }
                            String claimedPremiumStr = rs.getString("claimed_premium");
                            if (claimedPremiumStr != null && !claimedPremiumStr.isEmpty()) {
                                Arrays.stream(claimedPremiumStr.split(",")).filter(s -> !s.isEmpty()).map(Integer::parseInt).forEach(claimedData.get("premium")::add);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
            return claimedData;
        }, connections.getExecutor());
    }

    public CompletableFuture<Boolean> savePlayerData(UUID uuid, PlayerData data) {
        return CompletableFuture.supplyAsync(() -> {
            String updateSql = "UPDATE " + connections.getPrefix() + "players SET xp=?, level=?, claimed_free=?, claimed_premium=?, last_notification=?, total_levels=?, has_premium=?, last_daily_reward=?, battle_coins=?, exclude_from_top=? WHERE uuid=?";
            String missionSql = connections.isMySQL()
                    ? "INSERT INTO " + connections.getPrefix() + "missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE progress = VALUES(progress)"
                    : "INSERT OR REPLACE INTO " + connections.getPrefix() + "missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?)";

            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, data.xp);
                    ps.setInt(2, data.level);
                    ps.setString(3, data.claimedFreeRewards.stream().map(String::valueOf).collect(Collectors.joining(",")));
                    ps.setString(4, data.claimedPremiumRewards.stream().map(String::valueOf).collect(Collectors.joining(",")));
                    ps.setInt(5, data.lastNotification);
                    ps.setInt(6, data.totalLevels);
                    ps.setInt(7, data.hasPremium ? 1 : 0);
                    ps.setLong(8, data.lastDailyReward);
                    ps.setInt(9, data.battleCoins);
                    ps.setInt(10, data.excludeFromTop ? 1 : 0);
                    ps.setString(11, uuid.toString());
                    ps.executeUpdate();
                }

                String missionDate = currentMissionDate.get();
                if (!data.missionProgress.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(missionSql)) {
                        for (Map.Entry<String, Integer> mission : data.missionProgress.entrySet()) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, mission.getKey());
                            ps.setInt(3, Math.max(0, mission.getValue()));
                            ps.setString(4, missionDate);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save data for " + uuid + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
    }

    public CompletableFuture<Void> updatePlayerCoins(UUID uuid, int amount) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + connections.getPrefix() + "players SET battle_coins = ? WHERE uuid = ?")) {
                    ps.setInt(1, amount);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
    }

    public CompletableFuture<List<PlayerData>> getTop10Players() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> allPlayers = new ArrayList<>();
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + connections.getPrefix() + "players WHERE exclude_from_top = 0 ORDER BY total_levels DESC, level DESC, xp DESC LIMIT 10")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
                            data.xp = rs.getInt("xp");
                            data.level = rs.getInt("level");
                            data.totalLevels = rs.getInt("total_levels");
                            data.battleCoins = rs.getInt("battle_coins");
                            data.excludeFromTop = rs.getInt("exclude_from_top") == 1;
                            allPlayers.add(data);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
            return allPlayers;
        }, connections.getExecutor());
    }

    public CompletableFuture<Void> resetSeason() {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (Statement stmt = conn.createStatement()) {
                    boolean resetCoins = plugin.getConfigManager().isResetCoinsOnSeasonEnd();

                    if (resetCoins) {
                        stmt.executeUpdate("UPDATE " + connections.getPrefix() + "players SET xp = 0, level = 1, claimed_free = '', claimed_premium = '', has_premium = 0, last_daily_reward = 0, battle_coins = 0");
                    } else {
                        stmt.executeUpdate("UPDATE " + connections.getPrefix() + "players SET xp = 0, level = 1, claimed_free = '', claimed_premium = '', has_premium = 0, last_daily_reward = 0");
                    }
                    stmt.executeUpdate("DELETE FROM " + connections.getPrefix() + "missions");
                    stmt.executeUpdate("DELETE FROM " + connections.getPrefix() + "daily_missions");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
    }
}
