package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private List<Integer> coinsDistribution = new ArrayList<>();
    private boolean shopEnabled = true;
    private boolean resetCoinsOnSeasonEnd = true;
    private int coinsDistributionHours = 24;
    private int missionResetHours = 24;
    private boolean customItemSoundsEnabled = true;

    public ConfigManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        config = plugin.getConfig();
        xpPerLevel = config.getInt("experience.xp-per-level", 200);
        seasonDuration = config.getInt("season.duration", 30);
        dailyRewardXP = config.getInt("daily-reward.xp", 200);
        shopEnabled = config.getBoolean("shop.enabled", true);
        resetCoinsOnSeasonEnd = config.getBoolean("season.reset-coins-on-season-end", true);
        coinsDistributionHours = config.getInt("battle-coins.distribution-hours", 24);
        missionResetHours = config.getInt("missions.reset-hours", 24);
        customItemSoundsEnabled = config.getBoolean("custom-items.sounds-enabled", true);

        coinsDistribution.clear();
        for (int i = 1; i <= 10; i++) {
            coinsDistribution.add(config.getInt("battle-coins.distribution." + i, 11 - i));
        }

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

    public List<Integer> getCoinsDistribution() {
        return coinsDistribution;
    }

    public boolean isShopEnabled() {
        return shopEnabled;
    }

    public boolean isResetCoinsOnSeasonEnd() {
        return resetCoinsOnSeasonEnd;
    }

    public int getCoinsDistributionHours() {
        return coinsDistributionHours;
    }

    public int getMissionResetHours() {
        return missionResetHours;
    }

    public boolean isCustomItemSoundsEnabled() {
        return customItemSoundsEnabled;
    }
}