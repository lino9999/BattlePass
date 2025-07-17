package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final BattlePass plugin;
    private FileConfiguration config;
    private FileConfiguration missionsConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration battlePassFreeConfig;
    private FileConfiguration battlePassPremiumConfig;

    private int xpPerLevel = 200;
    private int dailyMissionsCount = 7;
    private int seasonDuration = 30;
    private int dailyRewardXP = 200;

    public ConfigManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        config = plugin.getConfig();
        xpPerLevel = config.getInt("experience.xp-per-level", 200);
        seasonDuration = config.getInt("season.duration", 30);
        dailyRewardXP = config.getInt("daily-reward.xp", 200);

        File missionsFile = new File(plugin.getDataFolder(), "missions.yml");
        missionsConfig = YamlConfiguration.loadConfiguration(missionsFile);
        dailyMissionsCount = missionsConfig.getInt("daily-missions-count", 7);

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        File battlePassFreeFile = new File(plugin.getDataFolder(), "BattlePassFREE.yml");
        if (!battlePassFreeFile.exists()) {
            plugin.saveResource("BattlePassFREE.yml", false);
        }
        battlePassFreeConfig = YamlConfiguration.loadConfiguration(battlePassFreeFile);

        File battlePassPremiumFile = new File(plugin.getDataFolder(), "BattlePassPREMIUM.yml");
        if (!battlePassPremiumFile.exists()) {
            plugin.saveResource("BattlePassPREMIUM.yml", false);
        }
        battlePassPremiumConfig = YamlConfiguration.loadConfiguration(battlePassPremiumFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMissionsConfig() {
        return missionsConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getBattlePassFreeConfig() {
        return battlePassFreeConfig;
    }

    public FileConfiguration getBattlePassPremiumConfig() {
        return battlePassPremiumConfig;
    }

    public int getXpPerLevel() {
        return xpPerLevel;
    }

    public int getDailyMissionsCount() {
        return dailyMissionsCount;
    }

    public int getSeasonDuration() {
        return seasonDuration;
    }

    public int getDailyRewardXP() {
        return dailyRewardXP;
    }
}