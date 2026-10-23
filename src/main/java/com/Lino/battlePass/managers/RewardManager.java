package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewardManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final RewardClaimHandler claimHandler;

    private final Map<Integer, List<Reward>> freeRewardsByLevel = new HashMap<>();
    private final Map<Integer, List<Reward>> premiumRewardsByLevel = new HashMap<>();

    private int maxLevel = 54;
    private static final Pattern LEVEL_PATTERN = Pattern.compile("level-(\\d+)");

    public RewardManager(BattlePass plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.claimHandler = new RewardClaimHandler(plugin, configManager);
    }

    public void loadRewards() {
        freeRewardsByLevel.clear();
        premiumRewardsByLevel.clear();

        FileConfiguration freeConfig = configManager.getBattlePassFreeConfig();
        FileConfiguration premiumConfig = configManager.getBattlePassPremiumConfig();

        maxLevel = 54;
        updateMaxLevel(freeConfig);
        updateMaxLevel(premiumConfig);

        for (int i = 1; i <= maxLevel; i++) {
            String levelPath = "level-" + i;
            List<Reward> freeLevel = new ArrayList<>();
            List<Reward> premiumLevel = new ArrayList<>();

            if (freeConfig.contains(levelPath)) {
                if (freeConfig.contains(levelPath + ".material") || freeConfig.contains(levelPath + ".command")) {
                    Reward reward = loadSingleReward(freeConfig, levelPath, i, true);
                    if (reward != null) {
                        freeLevel.add(reward);
                    }
                } else if (freeConfig.contains(levelPath + ".items")) {
                    for (String key : freeConfig.getConfigurationSection(levelPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(freeConfig, levelPath + ".items." + key, i, true);
                        if (reward != null) {
                            freeLevel.add(reward);
                        }
                    }
                }
            }

            if (premiumConfig.contains(levelPath)) {
                if (premiumConfig.contains(levelPath + ".material") || premiumConfig.contains(levelPath + ".command")) {
                    Reward reward = loadSingleReward(premiumConfig, levelPath, i, false);
                    if (reward != null) {
                        premiumLevel.add(reward);
                    }
                } else if (premiumConfig.contains(levelPath + ".items")) {
                    for (String key : premiumConfig.getConfigurationSection(levelPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(premiumConfig, levelPath + ".items." + key, i, false);
                        if (reward != null) {
                            premiumLevel.add(reward);
                        }
                    }
                }
            }

            if (!freeLevel.isEmpty()) freeRewardsByLevel.put(i, freeLevel);
            if (!premiumLevel.isEmpty()) premiumRewardsByLevel.put(i, premiumLevel);
        }
    }

    private void updateMaxLevel(FileConfiguration config) {
        for (String key : config.getKeys(false)) {
            Matcher matcher = LEVEL_PATTERN.matcher(key);
            if (matcher.matches()) {
                try {
                    int level = Integer.parseInt(matcher.group(1));
                    if (level > maxLevel) {
                        maxLevel = level;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private Reward loadSingleReward(FileConfiguration config, String path, int level, boolean isFree) {
        if (config.contains(path + ".command")) {
            String command = config.getString(path + ".command");
            String displayName = config.getString(path + ".display", "Mystery Reward");
            return new Reward(level, command, displayName, isFree);
        } else if (config.contains(path + ".material")) {
            String material = config.getString(path + ".material", "DIRT");
            int amount = Math.max(1, config.getInt(path + ".amount", 1));

            try {
                Material mat = Material.valueOf(material.toUpperCase());
                String displayName = config.getString(path + ".display", amount + "x " + formatMaterial(mat));
                return new Reward(level, mat, displayName, amount, isFree, path);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material for " + (isFree ? "free" : "premium") +
                        " level " + level + ": " + material);
                return null;
            }
        }
        return null;
    }

    public void claimRewards(Player player, PlayerData data, List<Reward> rewards,
                             int level, boolean isPremium) {
        claimHandler.claimRewards(player, data, rewards, level, isPremium);
    }

    public int countAvailableRewards(PlayerData data) {
        int count = 0;
        for (int level : freeRewardsByLevel.keySet()) {
            if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                count++;
            }
        }

        if (data.hasPremium) {
            for (int level : premiumRewardsByLevel.keySet()) {
                if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                    count++;
                }
            }
        }

        return count;
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    public Map<Integer, List<Reward>> getFreeRewardsByLevel() {
        return freeRewardsByLevel;
    }

    public Map<Integer, List<Reward>> getPremiumRewardsByLevel() {
        return premiumRewardsByLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getMaxPage() {
        return (int) Math.ceil(getMaxLevel() / 9.0);
    }

    public List<EditableReward> getEditableRewards(int level, boolean isPremium) {
        List<EditableReward> editableRewards = new ArrayList<>();
        FileConfiguration config = isPremium ?
                configManager.getBattlePassPremiumConfig() :
                configManager.getBattlePassFreeConfig();
        String levelPath = "level-" + level;

        if (!config.contains(levelPath)) {
            return editableRewards;
        }

        ConfigurationSection levelSection = config.getConfigurationSection(levelPath);
        if (levelSection == null) {
            return editableRewards;
        }

        if (levelSection.contains("command")) {
            String command = levelSection.getString("command");
            String display = levelSection.getString("display", "Mystery Reward");
            editableRewards.add(new EditableReward(command, display));
        } else if (levelSection.contains("material")) {
            ItemStack item = ItemSerializer.loadItemFromConfig(levelSection);
            if (item != null) {
                editableRewards.add(new EditableReward(item));
            }
        } else if (levelSection.contains("items")) {
            ConfigurationSection itemsSection = levelSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                    if (itemSection != null) {
                        if (itemSection.contains("command")) {
                            String command = itemSection.getString("command");
                            String display = itemSection.getString("display", "Mystery Reward");
                            editableRewards.add(new EditableReward(command, display));
                        } else {
                            ItemStack item = ItemSerializer.loadItemFromConfig(itemSection);
                            if (item != null) {
                                editableRewards.add(new EditableReward(item));
                            }
                        }
                    }
                }
            }
        }

        return editableRewards;
    }
}
