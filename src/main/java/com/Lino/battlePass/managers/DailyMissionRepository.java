package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.Mission;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Persists active daily missions and removes expired mission progress. */
final class DailyMissionRepository {

    private final DatabaseConnections connections;
    private final Supplier<String> currentMissionDate;

    DailyMissionRepository(DatabaseConnections connections, Supplier<String> currentMissionDate) {
        this.connections = connections;
        this.currentMissionDate = currentMissionDate;
    }

    public CompletableFuture<Void> saveDailyMissions(List<Mission> missions, String missionDate) {
        return CompletableFuture.runAsync(() -> {
            if (missions.isEmpty()) return;

            String insertSql = connections.isMySQL()
                    ? "INSERT INTO " + connections.getPrefix() + "daily_missions (name, type, target, additional_targets, required, xp_reward, date) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), type=VALUES(type), target=VALUES(target), additional_targets=VALUES(additional_targets), required=VALUES(required), xp_reward=VALUES(xp_reward)"
                    : "INSERT OR REPLACE INTO " + connections.getPrefix() + "daily_missions (name, type, target, additional_targets, required, xp_reward, date) VALUES (?, ?, ?, ?, ?, ?, ?)";

            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + connections.getPrefix() + "daily_missions")) {
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (Mission mission : missions) {
                        ps.setString(1, mission.name);
                        ps.setString(2, mission.type);
                        ps.setString(3, mission.target);
                        ps.setString(4, String.join(",", mission.additionalTargets));
                        ps.setInt(5, mission.required);
                        ps.setInt(6, mission.xpReward);
                        ps.setString(7, missionDate);
                        ps.addBatch();
                    }
                    ps.executeBatch();
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

    public CompletableFuture<List<Mission>> loadDailyMissions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Mission> loadedMissions = new ArrayList<>();
            String missionDate = currentMissionDate.get();
            if (missionDate == null) missionDate = LocalDateTime.now().toLocalDate().toString();

            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + connections.getPrefix() + "daily_missions WHERE date = ? ORDER BY id")) {
                    ps.setString(1, missionDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String additionalTargetsStr = rs.getString("additional_targets");
                            List<String> additionalTargets = new ArrayList<>();
                            if (additionalTargetsStr != null && !additionalTargetsStr.isEmpty()) {
                                additionalTargets = Arrays.asList(additionalTargetsStr.split(","));
                            }

                            loadedMissions.add(new Mission(
                                    rs.getString("name"),
                                    rs.getString("type"),
                                    rs.getString("target"),
                                    additionalTargets,
                                    rs.getInt("required"),
                                    rs.getInt("xp_reward")
                            ));
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
            return loadedMissions;
        }, connections.getExecutor());
    }

    public CompletableFuture<Void> clearOldMissionProgress(String currentDate) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + connections.getPrefix() + "missions WHERE date < ?")) {
                    ps.setString(1, currentDate);
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
}
