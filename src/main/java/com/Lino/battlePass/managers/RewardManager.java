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
        Set<Integer> levels = new TreeSet<>();
        collectLevels(freeConfig, levels);
        collectLevels(premiumConfig, levels);
        for (int level : levels) {
            List<Reward> free = loadLevel(freeConfig, level, true);
            List<Reward> premium = loadLevel(premiumConfig, level, false);
            if (!free.isEmpty()) freeRewardsByLevel.put(level, free);
            if (!premium.isEmpty()) premiumRewardsByLevel.put(level, premium);
        }
    }

    private void collectLevels(FileConfiguration config, Set<Integer> levels) {
        for (String key : config.getKeys(false)) {
            Matcher matcher = LEVEL_PATTERN.matcher(key);
            if (!matcher.matches()) continue;
            try {
                int level = Integer.parseInt(matcher.group(1));
                if (level > 0 && key.equals("level-" + level)) {
                    levels.add(level);
                    maxLevel = Math.max(maxLevel, level);
                }
            } catch (NumberFormatException ignored) { }
        }
    }

    private List<Reward> loadLevel(FileConfiguration config, int level, boolean free) {
        List<Reward> rewards = new ArrayList<>();
        String path = "level-" + level;
        if (config.contains(path + ".material") || config.contains(path + ".command")) {
            Reward reward = loadSingleReward(config, path, level, free);
            if (reward != null) rewards.add(reward);
        } else if (config.contains(path + ".items")) {
            ConfigurationSection items = config.getConfigurationSection(path + ".items");
            if (items == null) {
                plugin.getLogger().warning("Invalid reward items section at " + path + " (" + (free ? "free" : "premium") + "). Skipping.");
                return rewards;
            }
            for (String key : items.getKeys(false)) {
                Reward reward = loadSingleReward(config, path + ".items." + key, level, free);
                if (reward != null) rewards.add(reward);
            }
        }
        return rewards;
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
                Material mat = Material.valueOf(material.toUpperCase(Locale.ROOT));
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
        return material.name().toLowerCase(Locale.ROOT).replace("_", " ");
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
