package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionManagerSeasonTest {
    private BattlePass plugin;
    private ConfigManager config;
    private DatabaseManager database;
    private PlayerDataManager players;
    private MockedStatic<Bukkit> bukkit;
    private final Queue<Runnable> mainThreadTasks = new ArrayDeque<>();
    private LocalDateTime savedEnd;
    private LocalDateTime savedStart;
    private final CompletableFuture<Void> resetComplete = new CompletableFuture<>();

    @BeforeEach
    void setUp() {
        plugin = mock(BattlePass.class);
        config = mock(ConfigManager.class);
        database = mock(DatabaseManager.class);
        players = mock(PlayerDataManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getPlayerDataManager()).thenReturn(players);
        when(plugin.getMessageManager()).thenReturn(mock(MessageManager.class));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(config.getSeasonResetType()).thenReturn("DURATION");
        when(config.getMissionResetHours()).thenReturn(24);
        when(config.getMissionsConfig()).thenReturn(new YamlConfiguration());
        when(database.resetSeason()).thenReturn(resetComplete);
        when(database.saveSeasonData(any(), any(), any(), any())).thenAnswer(invocation -> {
            savedStart = invocation.getArgument(0);
            savedEnd = invocation.getArgument(1);
            return CompletableFuture.completedFuture(null);
        });
        when(database.loadDailyMissions()).thenReturn(CompletableFuture.completedFuture(List.of(mock(Mission.class))));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            mainThreadTasks.add(invocation.getArgument(1));
            return null;
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            mainThreadTasks.add(invocation.getArgument(1));
            return null;
        });
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {90, 150})
    void manualResetPersistsTheFullDurationBeforeDatabaseCallback(int days) {
        when(config.getSeasonDuration()).thenReturn(days);
        MissionManager manager = initialize(LocalDateTime.now().plusDays(2));
        LocalDateTime before = LocalDateTime.now();

        manager.forceResetSeason();

        assertNotNull(savedEnd);
        assertFalse(savedEnd.isBefore(before.plusDays(days)), "The previous season deadline must never be saved for the new season");
        assertFalse(savedEnd.isAfter(LocalDateTime.now().plusDays(days)));
        verify(database, never()).saveDailyMissions(any(), any());
        resetComplete.complete(null);
        runMainThreadTasks();
        verify(database).saveDailyMissions(any(), any());

        LocalDateTime newEnd = savedEnd;
        initialize(savedStart, savedEnd);
        assertEquals(newEnd, savedEnd);
        verify(database, times(1)).resetSeason();
    }

    @ParameterizedTest
    @ValueSource(ints = {90, 150})
    void expiredSeasonPersistsNewDeadlineAndDoesNotResetAgainWhileDatabaseIsPending(int days) {
        when(config.getSeasonDuration()).thenReturn(days);
        LocalDateTime before = LocalDateTime.now();
        MissionManager manager = initialize(before.minusDays(1));
        runMainThreadTasks();

        assertNotNull(savedEnd);
        assertFalse(savedEnd.isBefore(before.plusDays(days)), "An expired deadline would reset the season again after a restart");
        manager.checkSeasonReset();
        manager.forceResetSeason();
        verify(database, times(1)).resetSeason();

        LocalDateTime persistedDeadline = savedEnd;
        resetComplete.complete(null);
        runMainThreadTasks();
        manager.shutdown();
        assertEquals(persistedDeadline, savedEnd, "Database completion must not change the deadline after it was saved");
    }

    @Test
    void durationChangeOnReloadUsesOriginalStartAndSurvivesRestarts() {
        when(config.getSeasonDuration()).thenReturn(30);
        LocalDateTime start = LocalDateTime.now().minusDays(12);
        MissionManager manager = initialize(start, start.plusDays(30));

        when(config.getSeasonDuration()).thenReturn(150);
        manager.recalculateResetTimeOnReload();
        assertEquals(start.plusDays(150), savedEnd);
        manager.recalculateResetTimeOnReload();
        assertEquals(start.plusDays(150), savedEnd);

        initialize(savedStart, savedEnd);
        assertEquals(start, savedStart);
        assertEquals(start.plusDays(150), savedEnd);
        verify(database, never()).resetSeason();
        verify(players, never()).clearCache(anyBoolean());
    }

    @Test
    void durationChangeOnRestartIsAppliedBeforeCheckingOldExpiry() {
        when(config.getSeasonDuration()).thenReturn(150);
        LocalDateTime start = LocalDateTime.now().minusDays(40);
        initialize(start, start.plusDays(30));

        assertEquals(start.plusDays(150), savedEnd);
        verify(database, never()).resetSeason();
    }

    @Test
    void shorteningDurationPastElapsedTimeTriggersOneReset() {
        when(config.getSeasonDuration()).thenReturn(150);
        LocalDateTime start = LocalDateTime.now().minusDays(40);
        MissionManager manager = initialize(start, start.plusDays(150));
        when(config.getSeasonDuration()).thenReturn(30);

        manager.recalculateResetTimeOnReload();
        assertEquals(start.plusDays(30), savedEnd);
        manager.checkSeasonReset();
        manager.checkSeasonReset();
        verify(database, times(1)).resetSeason();
        assertTrue(savedStart.isAfter(start));
        assertEquals(savedStart.plusDays(30), savedEnd);
    }

    @Test
    void legacyDurationSeasonGetsOneCountdownMigrationWithoutLosingProgress() {
        when(config.getSeasonDuration()).thenReturn(150);
        LocalDateTime before = LocalDateTime.now();
        when(database.loadSeasonData()).thenReturn(CompletableFuture.completedFuture(Map.of(
                "endDate", before.plusDays(3),
                "missionResetTime", before.plusHours(24),
                "currentMissionDate", before.toLocalDate().toString())));
        MissionManager manager = new MissionManager(plugin, config, database, players);
        manager.initialize();
        runMainThreadTasks();

        assertFalse(savedStart.isBefore(before));
        assertEquals(savedStart.plusDays(150), savedEnd);
        LocalDateTime migratedEnd = savedEnd;
        initialize(savedStart, savedEnd);
        assertEquals(migratedEnd, savedEnd);
        verify(database, never()).resetSeason();
        verify(players, never()).clearCache(anyBoolean());
    }

    @Test
    void firstInstallPersistsTheStartAndConfiguredDuration() {
        when(config.getSeasonDuration()).thenReturn(90);
        when(database.loadSeasonData()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        LocalDateTime before = LocalDateTime.now();
        MissionManager manager = new MissionManager(plugin, config, database, players);
        manager.initialize();
        runMainThreadTasks();

        assertFalse(savedStart.isBefore(before));
        assertEquals(savedStart.plusDays(90), savedEnd);
        assertTrue(manager.isInitialized());
        verify(database, never()).resetSeason();
    }

    @Test
    void legacyMonthStartKeepsItsExistingCalendarDeadline() {
        when(config.getSeasonResetType()).thenReturn("MONTH_START");
        LocalDateTime end = LocalDateTime.now().withDayOfMonth(1).plusMonths(1).toLocalDate().atStartOfDay();
        when(database.loadSeasonData()).thenReturn(CompletableFuture.completedFuture(Map.of(
                "endDate", end,
                "missionResetTime", LocalDateTime.now().plusHours(24),
                "currentMissionDate", LocalDateTime.now().toLocalDate().toString())));
        MissionManager manager = new MissionManager(plugin, config, database, players);
        manager.initialize();
        runMainThreadTasks();

        assertEquals(end, savedEnd);
        verify(database, never()).resetSeason();
    }

    @Test
    void monthStartRemainsAtMidnightAcrossReloads() {
        when(config.getSeasonResetType()).thenReturn("MONTH_START");
        LocalDateTime start = LocalDateTime.now().withDayOfMonth(1);
        LocalDateTime end = start.plusMonths(1).toLocalDate().atStartOfDay();
        MissionManager manager = initialize(start, end);
        manager.recalculateResetTimeOnReload();

        assertEquals(end, savedEnd);
        verify(database, never()).resetSeason();
    }

    private MissionManager initialize(LocalDateTime end) {
        return initialize(end.minusDays(config.getSeasonDuration()), end);
    }

    private MissionManager initialize(LocalDateTime start, LocalDateTime end) {
        when(database.loadSeasonData()).thenReturn(CompletableFuture.completedFuture(Map.of(
                "startDate", start,
                "endDate", end,
                "missionResetTime", LocalDateTime.now().plusHours(24),
                "currentMissionDate", LocalDateTime.now().toLocalDate().toString())));
        MissionManager manager = new MissionManager(plugin, config, database, players);
        manager.initialize();
        runMainThreadTasks();
        return manager;
    }

    private void runMainThreadTasks() {
        while (!mainThreadTasks.isEmpty()) {
            mainThreadTasks.remove().run();
        }
    }
}
