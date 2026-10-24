package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Persists season dates and coin deadlines while preserving season save ordering. */
final class SeasonDataRepository {

    private final BattlePass plugin;
    private final DatabaseConnections connections;
    private CompletableFuture<Void> seasonSave = CompletableFuture.completedFuture(null);

    SeasonDataRepository(BattlePass plugin, DatabaseConnections connections) {
        this.plugin = plugin;
        this.connections = connections;
    }

    public synchronized CompletableFuture<Void> saveSeasonData(LocalDateTime startDate, LocalDateTime endDate,
                                                               LocalDateTime missionResetTime, String currentMissionDate, LocalDateTime cycleStart) {
        // Preserve submission order even with MySQL's multi-threaded executor. An older mission
        // save must never overwrite the deadline just published by a reset or config reload.
        seasonSave = seasonSave.handle((ignored, failure) -> null).thenRunAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                saveOnConnection(conn, startDate, endDate, missionResetTime, currentMissionDate, cycleStart);

            } catch (SQLException e) {
                throw new CompletionException("Failed to save season dates", e);
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
        return seasonSave;
    }

    void saveOnConnection(Connection conn, LocalDateTime startDate, LocalDateTime endDate,
                          LocalDateTime missionResetTime, String currentMissionDate, LocalDateTime cycleStart) throws SQLException {
        String sql = connections.isMySQL()
                ? "INSERT INTO " + connections.getPrefix() + "season_data (id, start_date, end_date, mission_reset_time, current_mission_date, mission_cycle_start) VALUES (1, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE start_date = VALUES(start_date), end_date = VALUES(end_date), mission_reset_time = VALUES(mission_reset_time), current_mission_date = VALUES(current_mission_date), mission_cycle_start = VALUES(mission_cycle_start)"
                : "INSERT INTO " + connections.getPrefix() + "season_data (id, start_date, end_date, mission_reset_time, current_mission_date, mission_cycle_start) VALUES (1, ?, ?, ?, ?, ?) " +
                  "ON CONFLICT(id) DO UPDATE SET start_date = excluded.start_date, end_date = excluded.end_date, mission_reset_time = excluded.mission_reset_time, current_mission_date = excluded.current_mission_date, mission_cycle_start = excluded.mission_cycle_start";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate != null ? startDate.toString() : "");
            ps.setString(2, endDate != null ? endDate.toString() : "");
            ps.setString(3, missionResetTime != null ? missionResetTime.toString() : "");
            ps.setString(4, currentMissionDate != null ? currentMissionDate : LocalDateTime.now().toLocalDate().toString());
            ps.setString(5, cycleStart != null ? cycleStart.toString() : "");
            ps.executeUpdate();
        }
    }

    public CompletableFuture<Void> saveCoinsDistributionTime(LocalDateTime nextDistribution) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                saveCoinsOnConnection(conn, nextDistribution);
            } catch (SQLException e) {
                throw new CompletionException("Failed to save the coin deadline", e);
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, connections.getExecutor());
    }

    void saveCoinsOnConnection(Connection conn, LocalDateTime nextDistribution) throws SQLException {
        String sql = connections.isMySQL()
                ? "INSERT INTO " + connections.getPrefix() + "season_data (id, next_coins_distribution) VALUES (1, ?) ON DUPLICATE KEY UPDATE next_coins_distribution = VALUES(next_coins_distribution)"
                : "INSERT INTO " + connections.getPrefix() + "season_data (id, next_coins_distribution) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET next_coins_distribution = excluded.next_coins_distribution";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nextDistribution.toString());
            ps.executeUpdate();
        }
    }

    public CompletableFuture<LocalDateTime> loadCoinsDistributionTime() {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT next_coins_distribution FROM " + connections.getPrefix() + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String timeStr = rs.getString("next_coins_distribution");
                            if (timeStr != null && !timeStr.isEmpty()) {
                                try {
                                    return LocalDateTime.parse(timeStr);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Invalid coins distribution time in DB: " + timeStr);
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException("Failed to load the coin deadline", e);
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
            return null;
        }, connections.getExecutor());
    }

    public CompletableFuture<Map<String, Object>> loadSeasonData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new HashMap<>();
            Connection conn = null;
            boolean shouldClose = connections.isMySQL();

            try {
                conn = connections.getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + connections.getPrefix() + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String transitionId = rs.getString("season_transition_id");
                            if (transitionId != null) {
                                int target;
                                try { target = Integer.parseInt(rs.getString("pending_rotation_season")); }
                                catch (NumberFormatException invalid) { throw new SQLException("Invalid pending season rotation target", invalid); }
                                if (transitionId.isBlank() || target < 0) throw new SQLException("Invalid pending season transition");
                                data.put("seasonTransitionId", transitionId);
                                data.put("pendingRotationSeason", target);
                            }
                            String startDateStr = rs.getString("start_date");
                            if (startDateStr != null && !startDateStr.isEmpty()) {
                                try {
                                    data.put("startDate", LocalDateTime.parse(startDateStr));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Invalid season start date in DB: " + startDateStr);
                                }
                            }
                            String endDateStr = rs.getString("end_date");
                            if (endDateStr != null && !endDateStr.isEmpty()) {
                                try {
                                    data.put("endDate", LocalDateTime.parse(endDateStr));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Invalid season end date in DB: " + endDateStr);
                                }
                            }

                            String resetTimeStr = rs.getString("mission_reset_time");
                            if (resetTimeStr != null && !resetTimeStr.isEmpty()) {
                                try {
                                    data.put("missionResetTime", LocalDateTime.parse(resetTimeStr));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Invalid mission reset time in DB: " + resetTimeStr);
                                }
                            }

                            String currentMissionDate = rs.getString("current_mission_date");
                            String cycleStartStr = rs.getString("mission_cycle_start");
                            if (cycleStartStr != null && !cycleStartStr.isBlank()) {
                                try {
                                    data.put("missionCycleStart", LocalDateTime.parse(cycleStartStr));
                                } catch (Exception failure) {
                                    plugin.getLogger().warning("Invalid mission cycle start in DB: " + cycleStartStr);
                                }
                            }
                            if (currentMissionDate != null && !currentMissionDate.isEmpty()) {
                                data.put("currentMissionDate", currentMissionDate);
                            }

                            String coinsDistStr = rs.getString("next_coins_distribution");
                            if (coinsDistStr != null && !coinsDistStr.isEmpty()) {
                                try {
                                    data.put("nextCoinsDistribution", LocalDateTime.parse(coinsDistStr));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Invalid coins distribution time in DB: " + coinsDistStr);
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException("Failed to load season data", e);
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
            return data;
        }, connections.getExecutor());
    }

    public String getCurrentMissionDate() {
        String date = LocalDateTime.now().toLocalDate().toString();
        Connection conn = null;
        boolean shouldClose = connections.isMySQL();

        try {
            conn = connections.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_mission_date FROM " + connections.getPrefix() + "season_data WHERE id = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String stored = rs.getString("current_mission_date");
                        if (stored != null && !stored.isBlank()) date = stored;
                    }
                }
            }
        } catch (SQLException e) {
            throw new CompletionException("Failed to load the current mission date", e);
        } finally {
            if (shouldClose && conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
        return date;
    }

    void awaitPendingSaves() {
        // Chained season writes may not have been submitted to the executor yet.
        try {
            seasonSave.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not finish saving season data before shutdown: " + e.getMessage());
        }
    }
}
