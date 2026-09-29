package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportedConfigurationTest {
    @TempDir
    Path dataFolder;

    @Test
    void customModelsWorldBlacklistAndActionbarDurationsLoadAndReload() throws Exception {
        BattlePass plugin = mock(BattlePass.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                gui:
                  reward-locked:
                    free:
                      material: STONE
                      custom-model-data: 101
                    premium: GRAY_STAINED_GLASS
                  navigation:
                    custom-model-data: 110
                missions:
                  disabled-worlds: [world_nether]
                  actionbar:
                    progress-duration: 3
                    completed-duration: 7
                """);
        when(plugin.getConfig()).thenReturn(yaml);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        for (String file : List.of("missions.yml", "messages.yml", "BattlePassFREE.yml", "BattlePassPREMIUM.yml")) {
            Files.writeString(dataFolder.resolve(file), "");
        }
        ConfigManager config = new ConfigManager(plugin);

        assertEquals(Material.STONE, config.getGuiFreeLockedMaterial());
        assertEquals(101, config.getGuiCustomModelData("gui.reward-locked.free"));
        assertEquals(110, config.getGuiNavigationCustomModelData());
        assertTrue(config.isMissionWorldDisabled("WORLD_NETHER"));
        assertFalse(config.isMissionWorldDisabled("world"));
        assertEquals(3, config.getActionbarProgressDuration());
        assertEquals(7, config.getActionbarCompletedDuration());

        yaml.set("gui.reward-locked.free", "CHEST");
        yaml.set("missions.disabled-worlds", List.of());
        yaml.set("missions.actionbar.progress-duration", 5);
        config.reload();
        assertEquals(Material.CHEST, config.getGuiFreeLockedMaterial());
        assertNull(config.getGuiCustomModelData("gui.reward-locked.free"));
        assertFalse(config.isMissionWorldDisabled("world_nether"));
        assertEquals(5, config.getActionbarProgressDuration());
    }
}
