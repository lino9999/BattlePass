package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final BattlePass plugin;
    private FileConfiguration config;
    private FileConfiguration missionsConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration battlePassFreeConfig;
    private FileConfiguration battlePassPremiumConfig;

    private int xpPerLevel = 200;
    private int dailyMissionsCount = 7;
    private String seasonResetType = "DURATION";
    private int seasonDuration = 30;
    private int dailyRewardXP = 200;
    private List<Integer> coinsDistribution = new ArrayList<>();
    private boolean shopEnabled = true;
    private boolean resetCoinsOnSeasonEnd = true;
    private int coinsDistributionHours = 24;
    private int missionResetHours = 24;
    private boolean customItemSoundsEnabled = true;

    private Material guiFreeLockedMaterial = Material.GRAY_STAINED_GLASS;
    private Material guiPremiumLockedMaterial = Material.GRAY_STAINED_GLASS;
    private Material guiPremiumNoPassMaterial = Material.IRON_BARS;
    private Material guiRewardAvailableMaterial = Material.CHEST;
    private Material guiSeparatorMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private Material guiFreeClaimedMaterial = Material.GREEN_STAINED_GLASS;
    private Material guiPremiumClaimedMaterial = Material.LIME_STAINED_GLASS;
    private boolean hideFreeClaimedRewards = false;
    private boolean hidePremiumClaimedRewards = false;
    private final Map<String, Integer> guiCustomModelData = new HashMap<>();
    private int guiNavigationCustomModelData = 0;
    private List<String> missionDisabledWorlds = new ArrayList<>();
    private int actionbarProgressDuration = 10;
    private int actionbarCompletedDuration = 15;

    private String databaseType;
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPass;
    private String dbPrefix;
    private int dbPoolSize;

    public ConfigManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        config = plugin.getConfig();
        xpPerLevel = Math.max(1, config.getInt("experience.xp-per-level", 200));
        seasonResetType = config.getString("season.reset-type", "DURATION");
        seasonDuration = Math.max(1, config.getInt("season.duration", 30));
        dailyRewardXP = Math.max(0, config.getInt("daily-reward.xp", 200));
        shopEnabled = config.getBoolean("shop.enabled", true);
        resetCoinsOnSeasonEnd = config.getBoolean("season.reset-coins-on-season-end", true);
        coinsDistributionHours = Math.max(1, config.getInt("battle-coins.distribution-hours", 24));
        missionResetHours = Math.max(1, config.getInt("missions.reset-hours", 24));
        customItemSoundsEnabled = config.getBoolean("custom-items.sounds-enabled", true);

        databaseType = config.getString("database.type", "SQLITE");
        dbHost = config.getString("database.host", "localhost");
        dbPort = config.getInt("database.port", 3306);
        dbName = config.getString("database.database", "battlepass");
        dbUser = config.getString("database.username", "root");
        dbPass = config.getString("database.password", "");
        dbPrefix = config.getString("database.prefix", "bp_");
        dbPoolSize = Math.max(1, config.getInt("database.pool-size", 10));

        guiCustomModelData.clear();

        guiFreeLockedMaterial = parseGuiMaterial("gui.reward-locked.free", Material.GRAY_STAINED_GLASS);
        guiPremiumLockedMaterial = parseGuiMaterial("gui.reward-locked.premium", Material.GRAY_STAINED_GLASS);
        guiPremiumNoPassMaterial = parseGuiMaterial("gui.premium-no-pass", Material.IRON_BARS);
        guiRewardAvailableMaterial = parseGuiMaterial("gui.reward-available", Material.CHEST);
        guiSeparatorMaterial = parseGuiMaterial("gui.separator", Material.GRAY_STAINED_GLASS_PANE);

        if (isGuiEntryNone("gui.reward-claimed.free")) {
            hideFreeClaimedRewards = true;
            guiFreeClaimedMaterial = null;
        } else {
            hideFreeClaimedRewards = false;
            guiFreeClaimedMaterial = parseGuiMaterial("gui.reward-claimed.free", Material.GREEN_STAINED_GLASS);
        }

        if (isGuiEntryNone("gui.reward-claimed.premium")) {
            hidePremiumClaimedRewards = true;
            guiPremiumClaimedMaterial = null;
        } else {
            hidePremiumClaimedRewards = false;
            guiPremiumClaimedMaterial = parseGuiMaterial("gui.reward-claimed.premium", Material.LIME_STAINED_GLASS);
        }

        guiNavigationCustomModelData = Math.max(0, config.getInt("gui.navigation.custom-model-data", 0));

        missionDisabledWorlds = config.getStringList("missions.disabled-worlds");
        actionbarProgressDuration = Math.max(1, config.getInt("missions.actionbar.progress-duration", 10));
        actionbarCompletedDuration = Math.max(1, config.getInt("missions.actionbar.completed-duration", 15));

        coinsDistribution.clear();
        for (int i = 1; i <= 10; i++) {
            coinsDistribution.add(config.getInt("battle-coins.distribution." + i, 11 - i));
        }

        File missionsFile = new File(plugin.getDataFolder(), "missions.yml");
        missionsConfig = YamlConfiguration.loadConfiguration(missionsFile);
        int configuredMissionsCount = missionsConfig.getInt("daily-missions-count", 7);
        if (configuredMissionsCount > 7) {
            plugin.getLogger().warning("daily-missions-count is set to " + configuredMissionsCount +
                    " but the missions GUI only shows 7 missions. Using 7.");
            configuredMissionsCount = 7;
        }
        dailyMissionsCount = Math.max(0, configuredMissionsCount);

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

    private Material parseMaterial(String materialName, Material defaultMaterial) {
        if (materialName == null || materialName.isEmpty()) {
            return defaultMaterial;
        }

        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' in config. Using default: " + defaultMaterial.name());
            return defaultMaterial;
        }
    }

    private boolean isGuiEntryNone(String path) {
        Object raw = config.get(path);
        String materialName;
        if (raw instanceof ConfigurationSection section) {
            materialName = section.getString("material");
        } else {
            materialName = config.getString(path);
        }
        return materialName != null && materialName.equalsIgnoreCase("NONE");
    }

    private Material parseGuiMaterial(String path, Material defaultMaterial) {
        Object raw = config.get(path);
        String materialName = null;
        int customModelData = 0;

        if (raw instanceof ConfigurationSection section) {
            materialName = section.getString("material");
            customModelData = section.getInt("custom-model-data", 0);
        } else if (raw instanceof String) {
            materialName = (String) raw;
        }

        if (customModelData > 0) {
            guiCustomModelData.put(path, customModelData);
        } else {
            guiCustomModelData.remove(path);
        }

        return parseMaterial(materialName, defaultMaterial);
    }

    public Integer getGuiCustomModelData(String path) {
        return guiCustomModelData.get(path);
    }

    public int getGuiNavigationCustomModelData() {
        return guiNavigationCustomModelData;
    }

    public boolean isMissionWorldDisabled(String worldName) {
        if (missionDisabledWorlds == null || worldName == null) return false;
        for (String world : missionDisabledWorlds) {
            if (world != null && world.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public int getActionbarProgressDuration() {
        return actionbarProgressDuration;
    }

    public int getActionbarCompletedDuration() {
        return actionbarCompletedDuration;
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

    public String getSeasonResetType() {
        return seasonResetType;
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

    public Material getGuiFreeLockedMaterial() {
        return guiFreeLockedMaterial;
    }

    public Material getGuiPremiumLockedMaterial() {
        return guiPremiumLockedMaterial;
    }

    public Material getGuiPremiumNoPassMaterial() {
        return guiPremiumNoPassMaterial;
    }

    public Material getGuiRewardAvailableMaterial() {
        return guiRewardAvailableMaterial;
    }

    public Material getGuiSeparatorMaterial() {
        return guiSeparatorMaterial;
    }

    public Material getGuiFreeClaimedMaterial() {
        return guiFreeClaimedMaterial;
    }

    public Material getGuiPremiumClaimedMaterial() {
        return guiPremiumClaimedMaterial;
    }

    public boolean shouldHideFreeClaimedRewards() {
        return hideFreeClaimedRewards;
    }

    public boolean shouldHidePremiumClaimedRewards() {
        return hidePremiumClaimedRewards;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }

    public String getDbPrefix() {
        return dbPrefix;
    }

    public int getDbPoolSize() {
        return dbPoolSize;
    }
}
