package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final BattlePass plugin;
    private FileConfiguration config;
    private FileConfiguration missionsConfig;
    private FileConfiguration messagesConfig;

    private int xpPerLevel = 200;
    private int dailyMissionsCount = 7;
    private int seasonDuration = 30;

    public ConfigManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        config = plugin.getConfig();
        xpPerLevel = config.getInt("experience.xp-per-level", 200);
        seasonDuration = config.getInt("season.duration", 30);

        File missionsFile = new File(plugin.getDataFolder(), "missions.yml");
        missionsConfig = YamlConfiguration.loadConfiguration(missionsFile);
        dailyMissionsCount = missionsConfig.getInt("daily-missions-count", 7);

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
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

    public int getXpPerLevel() {
        return xpPerLevel;
    }

    public int getDailyMissionsCount() {
        return dailyMissionsCount;
    }

    public int getSeasonDuration() {
        return seasonDuration;
    }
}