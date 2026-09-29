package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseSeasonDataTest {
    @TempDir
    Path dataFolder;
    private BattlePass plugin;
    private DatabaseManager database;

    @BeforeEach
    void setUp() {
        plugin = mock(BattlePass.class);
        ConfigManager config = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(config.getDatabaseType()).thenReturn("SQLITE");
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.shutdown();
    }

    @Test
    void migratesOldSchemaAndPersistsStartWithoutChangingCoinsScheduleOrPlayerProgress() throws Exception {
        Path databaseFile = Files.createDirectories(dataFolder.resolve("Database")).resolve("battlepass.db");
        LocalDateTime coinsTime = LocalDateTime.of(2026, 10, 1, 12, 0);
        LocalDateTime oldEnd = LocalDateTime.of(2026, 10, 20, 12, 0);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE season_data (id INTEGER PRIMARY KEY, end_date TEXT, " +
                    "mission_reset_time TEXT, current_mission_date TEXT, next_coins_distribution TEXT)");
            statement.executeUpdate("INSERT INTO season_data VALUES (1, '" + oldEnd + "', '', '2026-09-29', '" + coinsTime + "')");
        }

        startDatabase();
        Map<String, Object> legacy = database.loadSeasonData().get(5, TimeUnit.SECONDS);
        assertFalse(legacy.containsKey("startDate"));
        assertEquals(oldEnd, legacy.get("endDate"));
        assertEquals(coinsTime, legacy.get("nextCoinsDistribution"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO players (uuid, xp, level, has_premium) " +
                    "VALUES ('00000000-0000-0000-0000-000000000001', 7000, 36, 1)");
        }

        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 12, 34, 56, 123456789);
        database.saveSeasonData(start, start.plusDays(150), start.plusDays(1), "2026-09-29").get(5, TimeUnit.SECONDS);
        database.shutdown();
        startDatabase();

        Map<String, Object> restored = database.loadSeasonData().get(5, TimeUnit.SECONDS);
        assertEquals(start, restored.get("startDate"));
        assertEquals(start.plusDays(150), restored.get("endDate"));
        assertEquals(start.plusDays(1), restored.get("missionResetTime"));
        assertEquals("2026-09-29", restored.get("currentMissionDate"));
        assertEquals(coinsTime, restored.get("nextCoinsDistribution"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement();
             var player = statement.executeQuery("SELECT xp, level, has_premium FROM players")) {
            assertTrue(player.next());
            assertEquals(7000, player.getInt("xp"));
            assertEquals(36, player.getInt("level"));
            assertEquals(1, player.getInt("has_premium"));
        }
    }

    @Test
    void databaseInitializationFailureDoesNotReportSuccess() throws Exception {
        Files.createDirectories(dataFolder.resolve("Database").resolve("battlepass.db"));
        database = new DatabaseManager(plugin);

        assertThrows(CompletionException.class, () -> database.initialize().join());
    }

    @Test
    void upgradesLegacyMissionAndCoinColumnsWithoutDiscardingExistingMissions() throws Exception {
        Path databaseFile = Files.createDirectories(dataFolder.resolve("Database")).resolve("battlepass.db");
        String missionDate = LocalDateTime.now().toLocalDate().toString();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE season_data (id INTEGER PRIMARY KEY, end_date TEXT, " +
                    "mission_reset_time TEXT, current_mission_date TEXT)");
            statement.executeUpdate("INSERT INTO season_data VALUES (1, '', '', '" + missionDate + "')");
            statement.executeUpdate("CREATE TABLE daily_missions (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, type TEXT, target TEXT, required INTEGER, xp_reward INTEGER, date TEXT)");
            statement.executeUpdate("INSERT INTO daily_missions (name, type, target, required, xp_reward, date) " +
                    "VALUES ('Old mission', 'BREAK', 'STONE', 10, 100, '" + missionDate + "')");
        }

        startDatabase();
        var missions = database.loadDailyMissions().get(5, TimeUnit.SECONDS);
        assertEquals(1, missions.size());
        assertEquals("Old mission", missions.getFirst().name);
        assertTrue(missions.getFirst().additionalTargets.isEmpty());
        LocalDateTime coinsTime = LocalDateTime.now().plusHours(12);
        database.saveCoinsDistributionTime(coinsTime).get(5, TimeUnit.SECONDS);
        assertEquals(coinsTime, database.loadCoinsDistributionTime().get(5, TimeUnit.SECONDS));

        missions.getFirst().additionalTargets.add("DEEPSLATE");
        database.saveDailyMissions(missions, missionDate).get(5, TimeUnit.SECONDS);
        assertEquals(missions.getFirst().additionalTargets,
                database.loadDailyMissions().get(5, TimeUnit.SECONDS).getFirst().additionalTargets);
    }

    @Test
    void shutdownFlushesQueuedDeadlineChangesAndCoinsWritesPreserveDates() throws Exception {
        startDatabase();
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 12, 0);
        for (int days = 30; days <= 150; days++) {
            database.saveSeasonData(start, start.plusDays(days), start.plusDays(1), "2026-09-29");
        }
        database.saveCoinsDistributionTime(start.plusHours(12)).get(5, TimeUnit.SECONDS);
        database.shutdown();
        startDatabase();

        Map<String, Object> restored = database.loadSeasonData().get(5, TimeUnit.SECONDS);
        assertEquals(start, restored.get("startDate"));
        assertEquals(start.plusDays(150), restored.get("endDate"));
        assertEquals(start.plusHours(12), restored.get("nextCoinsDistribution"));
    }

    private void startDatabase() throws Exception {
        database = new DatabaseManager(plugin);
        database.initialize().get(5, TimeUnit.SECONDS);
    }
}
