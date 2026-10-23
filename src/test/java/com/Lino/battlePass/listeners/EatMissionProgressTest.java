package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.*;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EatMissionProgressTest {
    @ParameterizedTest
    @CsvSource({
            "GOLDEN_APPLE, HAND, 10", "GOLDEN_APPLE, OFF_HAND, 20",
            "ENCHANTED_GOLDEN_APPLE, HAND, 20", "ENCHANTED_GOLDEN_APPLE, OFF_HAND, 10"
    })
    void consumptionInEitherHandIncrementsOnlyMatchingMissionsOnce(Material material, EquipmentSlot hand,
                                                                   int foodLevel) throws Exception {
        BattlePass plugin = mock(BattlePass.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        PlayerData data = new PlayerData(uuid);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getFoodLevel()).thenReturn(foodLevel);
        PlayerDataManager players = mock(PlayerDataManager.class);
        when(plugin.getPlayerDataManager()).thenReturn(players);
        when(players.getPlayerData(uuid)).thenReturn(data);
        when(plugin.getMessageManager()).thenReturn(mock(MessageManager.class));
        ConfigManager config = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(config);

        List<Mission> missions = List.of(
                new Mission("Golden", "EAT_ITEM", "Golden Apple", List.of(), 5, 100),
                new Mission("Enchanted", "EAT_ITEM", "minecraft:enchanted_golden_apple", List.of(), 5, 100),
                new Mission("Any", "EAT_ITEM", "ANY", List.of(), 5, 100),
                new Mission("Both", "EAT_ITEM", "GOLDEN_APPLE", List.of("ENCHANTED_GOLDEN_APPLE"), 5, 100));
        MissionProgressTracker tracker = new MissionProgressTracker(plugin);
        MissionManager manager = mock(MissionManager.class);
        when(plugin.getMissionManager()).thenReturn(manager);
        doAnswer(invocation -> {
            tracker.trackProgress(invocation.getArgument(0), invocation.getArgument(1),
                    (String) invocation.getArgument(2), invocation.getArgument(3), missions);
            return null;
        }).when(manager).progressMission(eq(player), eq("EAT_ITEM"), anyString(), anyInt());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            MissionProgressListener listener = new MissionProgressListener(plugin);
            var handler = MissionProgressListener.class.getMethod("onPlayerConsume", PlayerItemConsumeEvent.class)
                    .getAnnotation(EventHandler.class);
            RegisteredListener registered = new RegisteredListener(listener,
                    (ignored, event) -> listener.onPlayerConsume((PlayerItemConsumeEvent) event),
                    handler.priority(), plugin, handler.ignoreCancelled());
            ItemStack stack = new ItemStack(material, 32);
            PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, stack, hand);

            event.setCancelled(true);
            registered.callEvent(event);
            assertTrue(data.missionProgress.isEmpty(), "Cancelled consumption must not award progress");

            event.setCancelled(false);
            registered.callEvent(event);
            for (Mission mission : missions) {
                String key = mission.type + "_" + mission.target + "_" + mission.required + "_" + mission.name.hashCode();
                boolean matches = mission.name.equals("Any") || mission.name.equals("Both")
                        || mission.name.equals(material == Material.GOLDEN_APPLE ? "Golden" : "Enchanted");
                assertEquals(matches ? 1 : 0, data.missionProgress.getOrDefault(key, 0), mission.name);
            }
            verify(manager).progressMission(player, "EAT_ITEM", material.name(), 1);
            verify(players).markForSave(uuid);
            assertEquals(32, stack.getAmount(), "The listener must leave inventory consumption to the server");
        }
    }
}
