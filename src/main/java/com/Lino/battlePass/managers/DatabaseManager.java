package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DatabaseManager {

    private final BattlePass plugin;
    private HikariDataSource dataSource;
    private Connection sqliteConnection;
    private final ExecutorService databaseExecutor;
    private boolean isMySQL;
    private String prefix;

    public DatabaseManager(BattlePass plugin) {
        this.plugin = plugin;
        String type = plugin.getConfigManager().getDatabaseType();
        if (type.equalsIgnoreCase("MYSQL")) {
            this.databaseExecutor = Executors.newFixedThreadPool(plugin.getConfigManager().getDbPoolSize());
        } else {
            this.databaseExecutor = Executors.newSingleThreadExecutor();
        }
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::initDatabase, databaseExecutor);
    }

    private void initDatabase() {
        try {
            String type = plugin.getConfigManager().getDatabaseType();
            this.isMySQL = type.equalsIgnoreCase("MYSQL");

            if (this.isMySQL) {
                this.prefix = plugin.getConfigManager().getDbPrefix();
                plugin.getLogger().info("Initializing MySQL connection...");
            } else {
                this.prefix = "";
                plugin.getLogger().info("Initializing SQLite connection...");
            }

            if (isMySQL) {
                setupMySQL();
            } else {
                setupSQLite();
            }
            createTables();
            plugin.getLogger().info("Database initialized successfully!");

        } catch (Throwable e) {
            plugin.getLogger().severe("CRITICAL DATABASE ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupMySQL() {
        HikariConfig config = new HikariConfig();
        ConfigManager cm = plugin.getConfigManager();

        String jdbcUrl = "jdbc:mysql://" + cm.getDbHost() + ":" + cm.getDbPort() + "/" + cm.getDbName() + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8";
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(cm.getDbUser());
        config.setPassword(cm.getDbPass());
        config.setMaximumPoolSize(cm.getDbPoolSize());
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Connected to MySQL database.");
    }

    private void setupSQLite() throws SQLException {
        File databaseDir = new File(plugin.getDataFolder(), "Database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }

        File dbFile = new File(databaseDir, "battlepass.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        sqliteConnection = DriverManager.getConnection(url);
        try (Statement stmt = sqliteConnection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        plugin.getLogger().info("Connected to SQLite database.");
    }

    private Connection getConnection() throws SQLException {
        if (isMySQL) {
            return dataSource.getConnection();
        } else {
            if (sqliteConnection == null || sqliteConnection.isClosed()) {
                setupSQLite();
            }
            return sqliteConnection;
        }
    }

    private void createTables() throws SQLException {
        Connection conn = getConnection();
        boolean shouldClose = isMySQL;

        try {
            try (Statement stmt = conn.createStatement()) {
                String autoIncrement = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
                String intKey = isMySQL ? "INT" : "INTEGER";
                String uuidType = isMySQL ? "VARCHAR(36)" : "TEXT";

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "players (" +
                        "uuid " + uuidType + " PRIMARY KEY," +
                        "xp INTEGER DEFAULT 0," +
                        "level INTEGER DEFAULT 1," +
                        "claimed_free TEXT," +
                        "claimed_premium TEXT," +
                        "last_notification INTEGER DEFAULT 0," +
                        "total_levels INTEGER DEFAULT 0," +
                        "has_premium INTEGER DEFAULT 0," +
                        "last_daily_reward BIGINT DEFAULT 0," +
                        "battle_coins INTEGER DEFAULT 0," +
                        "exclude_from_top INTEGER DEFAULT 0)"
                );

                String missionTableSql;
                if (isMySQL) {
                    missionTableSql = "CREATE TABLE IF NOT EXISTS " + prefix + "missions (" +
                            "uuid " + uuidType + "," +
                            "mission VARCHAR(255)," +
                            "progress INTEGER DEFAULT 0," +
                            "date VARCHAR(20)," +
                            "PRIMARY KEY (uuid, mission, date))";
                } else {
                    missionTableSql = "CREATE TABLE IF NOT EXISTS " + prefix + "missions (" +
                            "uuid " + uuidType + "," +
                            "mission TEXT," +
                            "progress INTEGER DEFAULT 0," +
                            "date TEXT," +
                            "PRIMARY KEY (uuid, mission, date))";
                }
                stmt.executeUpdate(missionTableSql);

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "season_data (" +
                        "id " + intKey + " PRIMARY KEY " + autoIncrement + "," +
                        "end_date TEXT," +
                        "mission_reset_time TEXT," +
                        "current_mission_date TEXT," +
                        "next_coins_distribution TEXT)"
                );

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "daily_missions (" +
                        "id " + intKey + " PRIMARY KEY " + autoIncrement + "," +
                        "name TEXT," +
                        "type TEXT," +
                        "target TEXT," +
                        "required INTEGER," +
                        "xp_reward INTEGER," +
                        "date TEXT)"
                );

                if (!isMySQL) {
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_missions_uuid_date ON " + prefix + "missions(uuid, date)");
                }
            }
        } finally {
            if (shouldClose && conn != null) {
                conn.close();
            }
        }
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = new PlayerData(uuid);
            String sqlSelect = "SELECT * FROM " + prefix + "players WHERE uuid = ?";
            String sqlInsert = isMySQL
                    ? "INSERT IGNORE INTO " + prefix + "players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels, has_premium, last_daily_reward, battle_coins, exclude_from_top) VALUES (?, 0, 1, '', '', 0, 0, 0, 0, 0, 0)"
                    : "INSERT OR IGNORE INTO " + prefix + "players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels, has_premium, last_daily_reward, battle_coins, exclude_from_top) VALUES (?, 0, 1, '', '', 0, 0, 0, 0, 0, 0)";

            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();

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

                String missionDate = getCurrentMissionDate();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "missions WHERE uuid = ? AND date = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, missionDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            data.missionProgress.put(rs.getString("mission"), rs.getInt("progress"));
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
        }, databaseExecutor);
    }

    public CompletableFuture<Map<String, Set<Integer>>> getClaimedRewards(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Set<Integer>> claimedData = new HashMap<>();
            claimedData.put("free", new HashSet<>());
            claimedData.put("premium", new HashSet<>());

            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT claimed_free, claimed_premium FROM " + prefix + "players WHERE uuid = ?")) {
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
        }, databaseExecutor);
    }

    public CompletableFuture<Boolean> savePlayerData(UUID uuid, PlayerData data) {
        return CompletableFuture.supplyAsync(() -> {
            String updateSql = "UPDATE " + prefix + "players SET xp=?, level=?, claimed_free=?, claimed_premium=?, last_notification=?, total_levels=?, has_premium=?, last_daily_reward=?, battle_coins=?, exclude_from_top=? WHERE uuid=?";
            String missionSql = isMySQL
                    ? "INSERT INTO " + prefix + "missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE progress = VALUES(progress)"
                    : "INSERT OR REPLACE INTO " + prefix + "missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?)";

            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
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

                String missionDate = getCurrentMissionDate();
                if (!data.missionProgress.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(missionSql)) {
                        for (Map.Entry<String, Integer> mission : data.missionProgress.entrySet()) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, mission.getKey());
                            ps.setInt(3, mission.getValue());
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
        }, databaseExecutor);
    }

    public CompletableFuture<Void> updatePlayerCoins(UUID uuid, int amount) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + prefix + "players SET battle_coins = ? WHERE uuid = ?")) {
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
        }, databaseExecutor);
    }

    public CompletableFuture<List<PlayerData>> getTop10Players() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> allPlayers = new ArrayList<>();
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "players WHERE exclude_from_top = 0 ORDER BY total_levels DESC, level DESC, xp DESC LIMIT 10")) {
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
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveSeasonData(LocalDateTime endDate, LocalDateTime missionResetTime, String currentMissionDate) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + prefix + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) exists = true;
                    }
                }

                if (exists) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE " + prefix + "season_data SET end_date = ?, mission_reset_time = ?, current_mission_date = ? WHERE id = 1")) {
                        ps.setString(1, endDate != null ? endDate.toString() : "");
                        ps.setString(2, missionResetTime != null ? missionResetTime.toString() : "");
                        ps.setString(3, currentMissionDate != null ? currentMissionDate : LocalDateTime.now().toLocalDate().toString());
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + prefix + "season_data (id, end_date, mission_reset_time, current_mission_date) VALUES (1, ?, ?, ?)")) {
                        ps.setString(1, endDate != null ? endDate.toString() : "");
                        ps.setString(2, missionResetTime != null ? missionResetTime.toString() : "");
                        ps.setString(3, currentMissionDate != null ? currentMissionDate : LocalDateTime.now().toLocalDate().toString());
                        ps.executeUpdate();
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveCoinsDistributionTime(LocalDateTime nextDistribution) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + prefix + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) exists = true;
                    }
                }

                if (exists) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE " + prefix + "season_data SET next_coins_distribution = ? WHERE id = 1")) {
                        ps.setString(1, nextDistribution.toString());
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + prefix + "season_data (id, next_coins_distribution) VALUES (1, ?)")) {
                        ps.setString(1, nextDistribution.toString());
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, databaseExecutor);
    }

    public CompletableFuture<LocalDateTime> loadCoinsDistributionTime() {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT next_coins_distribution FROM " + prefix + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String timeStr = rs.getString("next_coins_distribution");
                            if (timeStr != null && !timeStr.isEmpty()) {
                                return LocalDateTime.parse(timeStr);
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
            return null;
        }, databaseExecutor);
    }

    public CompletableFuture<Map<String, Object>> loadSeasonData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new HashMap<>();
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "season_data WHERE id = 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
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
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
            return data;
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveDailyMissions(List<Mission> missions, String missionDate) {
        return CompletableFuture.runAsync(() -> {
            if (missions.isEmpty()) return;

            String insertSql = isMySQL
                    ? "INSERT INTO " + prefix + "daily_missions (name, type, target, required, xp_reward, date) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name)"
                    : "INSERT OR REPLACE INTO " + prefix + "daily_missions (name, type, target, required, xp_reward, date) VALUES (?, ?, ?, ?, ?, ?)";

            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + prefix + "daily_missions")) {
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
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
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, databaseExecutor);
    }

    public CompletableFuture<List<Mission>> loadDailyMissions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Mission> loadedMissions = new ArrayList<>();
            String missionDate = getCurrentMissionDate();
            if (missionDate == null) missionDate = LocalDateTime.now().toLocalDate().toString();

            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + prefix + "daily_missions WHERE date = ? ORDER BY id")) {
                    ps.setString(1, missionDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            loadedMissions.add(new Mission(
                                    rs.getString("name"),
                                    rs.getString("type"),
                                    rs.getString("target"),
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
        }, databaseExecutor);
    }

    public String getCurrentMissionDate() {
        String date = LocalDateTime.now().toLocalDate().toString();
        Connection conn = null;
        boolean shouldClose = isMySQL;

        try {
            conn = getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_mission_date FROM " + prefix + "season_data WHERE id = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        date = rs.getString("current_mission_date");
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
        return date;
    }

    public CompletableFuture<Void> resetSeason() {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (Statement stmt = conn.createStatement()) {
                    boolean resetCoins = plugin.getConfigManager().isResetCoinsOnSeasonEnd();

                    if (resetCoins) {
                        stmt.executeUpdate("UPDATE " + prefix + "players SET xp = 0, level = 1, claimed_free = '', claimed_premium = '', has_premium = 0, last_daily_reward = 0, battle_coins = 0");
                    } else {
                        stmt.executeUpdate("UPDATE " + prefix + "players SET xp = 0, level = 1, claimed_free = '', claimed_premium = '', has_premium = 0, last_daily_reward = 0");
                    }
                    stmt.executeUpdate("DELETE FROM " + prefix + "missions");
                    stmt.executeUpdate("DELETE FROM " + prefix + "daily_missions");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (shouldClose && conn != null) {
                    try { conn.close(); } catch (SQLException e) {}
                }
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> clearOldMissionProgress(String currentDate) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            boolean shouldClose = isMySQL;

            try {
                conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + prefix + "missions WHERE date < ?")) {
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
        }, databaseExecutor);
    }

    public void shutdown() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            databaseExecutor.shutdownNow();
        }

        try {
            if (sqliteConnection != null && !sqliteConnection.isClosed()) {
                sqliteConnection.close();
            }
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}