package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.ConfigManager;
import com.Lino.battlePass.managers.DatabaseManager;
import com.Lino.battlePass.managers.MessageManager;
import com.Lino.battlePass.managers.PlayerDataManager;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinsDistributionTaskTest {
    @Test
    void overdueDeliveryAddsCoinsToCurrentBalanceOnceAndSavesNextDeadline() {
        BattlePass plugin = mock(BattlePass.class);
        ConfigManager config = mock(ConfigManager.class);
        DatabaseManager database = mock(DatabaseManager.class);
        PlayerDataManager players = mock(PlayerDataManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        MessageManager messages = mock(MessageManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getPlayerDataManager()).thenReturn(players);
        when(plugin.getMessageManager()).thenReturn(messages);
        when(config.isShopEnabled()).thenReturn(true);
        when(config.getCoinsDistributionHours()).thenReturn(24);
        when(config.getCoinsDistribution()).thenReturn(List.of(10));
        UUID uuid = UUID.randomUUID();
        PlayerData savedRanking = new PlayerData(uuid);
        savedRanking.battleCoins = 5;
        PlayerData current = new PlayerData(uuid);
        current.battleCoins = 50;
        when(players.getPlayerData(uuid)).thenReturn(current);
        when(database.getTop10Players()).thenReturn(CompletableFuture.completedFuture(List.of(savedRanking)));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("TestPlayer");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);
            CoinsDistributionTask task = new CoinsDistributionTask(plugin);
            task.setNextDistribution(LocalDateTime.now().minusMinutes(1));

            task.run();
            task.run();

            assertEquals(60, current.battleCoins);
            verify(players).markForSave(uuid);
            verify(database).getTop10Players();
            verify(database).saveCoinsDistributionTime(task.getNextDistribution());
            verify(database, never()).updatePlayerCoins(any(), anyInt());
        }
    }
}
