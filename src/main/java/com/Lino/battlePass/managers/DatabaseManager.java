package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Mission;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final BattlePass plugin;
    private Connection connection;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private PreparedStatement insertPlayerStmt;
    private PreparedStatement updatePlayerStmt;
    private PreparedStatement selectPlayerStmt;
    private PreparedStatement insertMissionStmt;

    public DatabaseManager(BattlePass plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::initDatabase, databaseExecutor);
    }

    private void initDatabase() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "battlepass.db");
            if (!dbFile.exists()) {
                plugin.getDataFolder().mkdirs();
            }

            Properties props = new Properties();
            props.setProperty("journal_mode", "WAL");
            props.setProperty("synchronous", "NORMAL");
            props.setProperty("temp_store", "MEMORY");
            props.setProperty("cache_size", "10000");

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), props);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=30000000000");

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS players (" +
                                "uuid TEXT PRIMARY KEY," +
                                "xp INTEGER DEFAULT 0," +
                                "level INTEGER DEFAULT 1," +
                                "claimed_free TEXT DEFAULT ''," +
                                "claimed_premium TEXT DEFAULT ''," +
                                "last_notification INTEGER DEFAULT 0," +
                                "total_levels INTEGER DEFAULT 0," +
                                "has_premium INTEGER DEFAULT 0)"
                );

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS missions (" +
                                "uuid TEXT," +
                                "mission TEXT," +
                                "progress INTEGER DEFAULT 0," +
                                "date TEXT," +
                                "PRIMARY KEY (uuid, mission, date))"
                );

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS season_data (" +
                                "id INTEGER PRIMARY KEY," +
                                "end_date TEXT," +
                                "duration INTEGER," +
                                "mission_reset_time TEXT," +
                                "current_mission_date TEXT)"
                );

                // Add current_mission_date column if it doesn't exist (for existing databases)
                try {
                    stmt.executeUpdate("ALTER TABLE season_data ADD COLUMN current_mission_date TEXT");
                } catch (SQLException e) {
                    // Column already exists, ignore
                }

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS daily_missions (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "name TEXT," +
                                "type TEXT," +
                                "target TEXT," +
                                "required INTEGER," +
                                "xp_reward INTEGER," +
                                "date TEXT)"
                );

                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_missions_uuid_date ON missions(uuid, date)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_players_total_levels ON players(total_levels DESC, level DESC, xp DESC)");
            }

            prepareDatabaseStatements();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void prepareDatabaseStatements() {
        try {
            insertPlayerStmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO players (uuid, xp, level, claimed_free, claimed_premium, " +
                            "last_notification, total_levels, has_premium) VALUES (?, 0, 1, '', '', 0, 0, 0)"
            );

            updatePlayerStmt = connection.prepareStatement(
                    "UPDATE players SET xp = ?, level = ?, claimed_free = ?, claimed_premium = ?, " +
                            "last_notification = ?, total_levels = ?, has_premium = ? WHERE uuid = ?"
            );

            selectPlayerStmt = connection.prepareStatement(
                    "SELECT * FROM players WHERE uuid = ?"
            );

            insertMissionStmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?)"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = new PlayerData(uuid);

            try {
                selectPlayerStmt.setString(1, uuid.toString());
                ResultSet rs = selectPlayerStmt.executeQuery();

                if (rs.next()) {
                    data.xp = rs.getInt("xp");
                    data.level = rs.getInt("level");
                    data.lastNotification = rs.getInt("last_notification");
                    data.totalLevels = rs.getInt("total_levels");
                    data.hasPremium = rs.getInt("has_premium") == 1;

                    String claimedFree = rs.getString("claimed_free");
                    if (!claimedFree.isEmpty()) {
                        for (String level : claimedFree.split(",")) {
                            if (!level.isEmpty()) {
                                data.claimedFreeRewards.add(Integer.parseInt(level));
                            }
                        }
                    }

                    String claimedPremium = rs.getString("claimed_premium");
                    if (!claimedPremium.isEmpty()) {
                        for (String level : claimedPremium.split(",")) {
                            if (!level.isEmpty()) {
                                data.claimedPremiumRewards.add(Integer.parseInt(level));
                            }
                        }
                    }
                } else {
                    insertPlayerStmt.setString(1, uuid.toString());
                    insertPlayerStmt.executeUpdate();
                }
                rs.close();

                // Load mission progress for the current mission date
                String missionDate = getCurrentMissionDate();
                plugin.getLogger().info("Loading mission progress for " + uuid + " for date: " + missionDate);

                PreparedStatement missionPs = connection.prepareStatement(
                        "SELECT * FROM missions WHERE uuid = ? AND date = ?"
                );
                missionPs.setString(1, uuid.toString());
                missionPs.setString(2, missionDate);
                ResultSet missionRs = missionPs.executeQuery();

                int missionCount = 0;
                while (missionRs.next()) {
                    String missionKey = missionRs.getString("mission");
                    int progress = missionRs.getInt("progress");
                    data.missionProgress.put(missionKey, progress);
                    missionCount++;
                    plugin.getLogger().info("  - Loaded mission '" + missionKey + "' with progress: " + progress);
                }
                plugin.getLogger().info("Loaded " + missionCount + " mission progress entries for " + uuid);

                missionRs.close();
                missionPs.close();

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return data;
        }, databaseExecutor);
    }

    public CompletableFuture<Void> savePlayerData(UUID uuid, PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                updatePlayerStmt.setInt(1, data.xp);
                updatePlayerStmt.setInt(2, data.level);
                updatePlayerStmt.setString(3, String.join(",",
                        data.claimedFreeRewards.stream().map(String::valueOf).toArray(String[]::new)));
                updatePlayerStmt.setString(4, String.join(",",
                        data.claimedPremiumRewards.stream().map(String::valueOf).toArray(String[]::new)));
                updatePlayerStmt.setInt(5, data.lastNotification);
                updatePlayerStmt.setInt(6, data.totalLevels);
                updatePlayerStmt.setInt(7, data.hasPremium ? 1 : 0);
                updatePlayerStmt.setString(8, uuid.toString());
                updatePlayerStmt.executeUpdate();

                // Save mission progress with the current mission date
                String missionDate = getCurrentMissionDate();
                plugin.getLogger().info("Saving mission progress for " + uuid + " for date: " + missionDate);

                for (Map.Entry<String, Integer> mission : data.missionProgress.entrySet()) {
                    insertMissionStmt.setString(1, uuid.toString());
                    insertMissionStmt.setString(2, mission.getKey());
                    insertMissionStmt.setInt(3, mission.getValue());
                    insertMissionStmt.setString(4, missionDate);
                    insertMissionStmt.executeUpdate();
                    plugin.getLogger().info("  - Saved mission '" + mission.getKey() + "' with progress: " + mission.getValue());
                }
                plugin.getLogger().info("Saved " + data.missionProgress.size() + " mission progress entries for " + uuid);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    public CompletableFuture<List<PlayerData>> getTop10Players() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> allPlayers = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM players ORDER BY total_levels DESC, level DESC, xp DESC LIMIT 10"
            )) {
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
                    data.xp = rs.getInt("xp");
                    data.level = rs.getInt("level");
                    data.totalLevels = rs.getInt("total_levels");
                    allPlayers.add(data);
                }
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return allPlayers;
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveSeasonData(LocalDateTime endDate, int duration, LocalDateTime missionResetTime, String currentMissionDate) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO season_data (id, end_date, duration, mission_reset_time, current_mission_date) VALUES (1, ?, ?, ?, ?)"
            )) {
                ps.setString(1, endDate != null ? endDate.toString() : LocalDateTime.now().plusDays(duration).toString());
                ps.setInt(2, duration);
                ps.setString(3, missionResetTime != null ? missionResetTime.toString() : "");
                ps.setString(4, currentMissionDate != null ? currentMissionDate : LocalDateTime.now().toLocalDate().toString());
                ps.executeUpdate();

                plugin.getLogger().info("Saved season data with mission date: " + currentMissionDate);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Map<String, Object>> loadSeasonData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new HashMap<>();

            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM season_data WHERE id = 1")) {
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    data.put("endDate", LocalDateTime.parse(rs.getString("end_date")));
                    data.put("duration", rs.getInt("duration"));
                    String resetTimeStr = rs.getString("mission_reset_time");
                    if (resetTimeStr != null && !resetTimeStr.isEmpty()) {
                        data.put("missionResetTime", LocalDateTime.parse(resetTimeStr));
                    }
                    String currentMissionDate = rs.getString("current_mission_date");
                    if (currentMissionDate != null && !currentMissionDate.isEmpty()) {
                        data.put("currentMissionDate", currentMissionDate);
                    }
                }
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return data;
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveDailyMissions(List<Mission> missions, String missionDate) {
        return CompletableFuture.runAsync(() -> {
            if (missions.isEmpty()) return;

            try {
                // First, clean up old missions (keep only current date)
                try (PreparedStatement deletePs = connection.prepareStatement(
                        "DELETE FROM daily_missions WHERE date != ?"
                )) {
                    deletePs.setString(1, missionDate);
                    deletePs.executeUpdate();
                }

                // Then insert new missions
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO daily_missions (name, type, target, required, xp_reward, date) " +
                                "VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    for (Mission mission : missions) {
                        ps.setString(1, mission.name);
                        ps.setString(2, mission.type);
                        ps.setString(3, mission.target);
                        ps.setInt(4, mission.required);
                        ps.setInt(5, mission.xpReward);
                        ps.setString(6, missionDate);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                plugin.getLogger().info("Saved " + missions.size() + " missions for date: " + missionDate);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    public CompletableFuture<List<Mission>> loadDailyMissions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Mission> loadedMissions = new ArrayList<>();

            // Get the current mission date from season data
            String missionDate = getCurrentMissionDate();
            if (missionDate == null) {
                missionDate = LocalDateTime.now().toLocalDate().toString();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM daily_missions WHERE date = ? ORDER BY id"
            )) {
                ps.setString(1, missionDate);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    Mission mission = new Mission(
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getString("target"),
                            rs.getInt("required"),
                            rs.getInt("xp_reward")
                    );
                    loadedMissions.add(mission);
                }
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return loadedMissions;
        }, databaseExecutor);
    }

    public String getCurrentMissionDate() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT current_mission_date FROM season_data WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String date = rs.getString("current_mission_date");
                rs.close();
                return date;
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Return today's date as fallback
        return LocalDateTime.now().toLocalDate().toString();
    }

    public CompletableFuture<Void> resetSeason() {
        return CompletableFuture.runAsync(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE players SET xp = 0, level = 1, claimed_free = '', " +
                        "claimed_premium = '', has_premium = 0");
                stmt.executeUpdate("DELETE FROM missions");
                stmt.executeUpdate("DELETE FROM daily_missions");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> clearOldMissionProgress(String currentDate) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM missions WHERE date < ?"
            )) {
                ps.setString(1, currentDate);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    public void shutdown() {
        databaseExecutor.shutdown();

        try {
            if (insertPlayerStmt != null) insertPlayerStmt.close();
            if (updatePlayerStmt != null) updatePlayerStmt.close();
            if (selectPlayerStmt != null) selectPlayerStmt.close();
            if (insertMissionStmt != null) insertMissionStmt.close();

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}