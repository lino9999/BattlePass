package com.Lino.battlePass;

import com.Lino.battlePass.managers.ConfigManager;
import com.Lino.battlePass.managers.DatabaseManager;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinsDistributionSchedulingTest {
    @Test
    void restartingManagedTaskPreservesDeadlineAndCancelsPreviousTask() {
        BattlePass plugin = mock(BattlePass.class);
        ConfigManager config = mock(ConfigManager.class);
        DatabaseManager database = mock(DatabaseManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask scheduled = mock(BukkitTask.class);
        when(scheduled.getTaskId()).thenReturn(7);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(1200L))).thenReturn(scheduled);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(config.getCoinsDistributionHours()).thenReturn(24);
        doCallRealMethod().when(plugin).startCoinsDistributionTask(any());
        when(plugin.getCoinsDistributionTask()).thenCallRealMethod();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            LocalDateTime due = LocalDateTime.now().plusMinutes(10);
            plugin.startCoinsDistributionTask(due);
            CoinsDistributionTask previous = plugin.getCoinsDistributionTask();

            plugin.startCoinsDistributionTask(null);

            assertNotSame(previous, plugin.getCoinsDistributionTask());
            assertEquals(due, plugin.getCoinsDistributionTask().getNextDistribution());
            verify(scheduler).cancelTask(7);
            verify(database, never()).saveCoinsDistributionTime(any());
        }
    }

    @Test
    void asyncDatabaseCallbackDefersTaskChangesToMainThread() {
        BattlePass plugin = mock(BattlePass.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doCallRealMethod().when(plugin).startCoinsDistributionTask(any());
        AtomicReference<Runnable> queued = new AtomicReference<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued.set(call.getArgument(1));
            return null;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            plugin.startCoinsDistributionTask(LocalDateTime.now());
            assertNotNull(queued.get());
            verify(plugin, never()).getConfigManager();
        }
    }
}
