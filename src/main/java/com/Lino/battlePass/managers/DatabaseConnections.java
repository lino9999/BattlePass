package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Owns database connections, schema initialization and the shared executor. */
final class DatabaseConnections {

    private final BattlePass plugin;
    private HikariDataSource dataSource;
    private Connection sqliteConnection;
    private final ExecutorService databaseExecutor;
    private boolean isMySQL;
    private String prefix;

    DatabaseConnections(BattlePass plugin) {
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

        } catch (Exception e) {
            throw new CompletionException("Database initialization or migration failed: " + e.getMessage(), e);
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

    Connection getConnection() throws SQLException {
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
                        "start_date TEXT," +
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
                        "additional_targets TEXT," +
                        "required INTEGER," +
                        "xp_reward INTEGER," +
                        "date TEXT)"
                );

                DatabaseSchema.ensureColumns(conn, prefix + "season_data", Map.of(
                        "start_date", "TEXT", "next_coins_distribution", "TEXT"));
                // MySQL rejects TEXT DEFAULT ''; existing rows can safely use NULL instead.
                DatabaseSchema.ensureColumns(conn, prefix + "daily_missions", Map.of("additional_targets", "TEXT"));
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

    boolean isMySQL() {
        return isMySQL;
    }

    String getPrefix() {
        return prefix;
    }

    ExecutorService getExecutor() {
        return databaseExecutor;
    }

    void shutdown() {
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
