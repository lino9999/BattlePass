package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private final Map<Integer, List<Reward>> freeRewardsByLevel = new HashMap<>();
    private final Map<Integer, List<Reward>> premiumRewardsByLevel = new HashMap<>();

    private int maxLevel = 54;
    private static final Pattern LEVEL_PATTERN = Pattern.compile("level-(\\d+)");

    public RewardManager(BattlePass plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadRewards() {
        freeRewards.clear();
        premiumRewards.clear();
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
                        freeRewards.add(reward);
                        freeLevel.add(reward);
                    }
                } else if (freeConfig.contains(levelPath + ".items")) {
                    for (String key : freeConfig.getConfigurationSection(levelPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(freeConfig, levelPath + ".items." + key, i, true);
                        if (reward != null) {
                            freeRewards.add(reward);
                            freeLevel.add(reward);
                        }
                    }
                }
            }

            if (premiumConfig.contains(levelPath)) {
                if (premiumConfig.contains(levelPath + ".material") || premiumConfig.contains(levelPath + ".command")) {
                    Reward reward = loadSingleReward(premiumConfig, levelPath, i, false);
                    if (reward != null) {
                        premiumRewards.add(reward);
                        premiumLevel.add(reward);
                    }
                } else if (premiumConfig.contains(levelPath + ".items")) {
                    for (String key : premiumConfig.getConfigurationSection(levelPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(premiumConfig, levelPath + ".items." + key, i, false);
                        if (reward != null) {
                            premiumRewards.add(reward);
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
                } catch (NumberFormatException ignored) {}
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
            int amount = config.getInt(path + ".amount", 1);

            try {
                Material mat = Material.valueOf(material.toUpperCase());
                String displayName = config.getString(path + ".display", amount + "x " + formatMaterial(mat));
                return new Reward(level, mat, displayName, amount, isFree);
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

        Map<String, Set<Integer>> dbClaimedData;
        try {
            dbClaimedData = plugin.getDatabaseManager().getClaimedRewards(player.getUniqueId()).join();
        } catch (Exception e) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cError while claiming reward. Please try again.");
            e.printStackTrace();
            return;
        }

        Set<Integer> dbClaimedSet = isPremium ? dbClaimedData.get("premium") : dbClaimedData.get("free");

        if (dbClaimedSet != null && dbClaimedSet.contains(level)) {
            MessageManager mm = plugin.getMessageManager();
            player.sendMessage(mm.getPrefix() + mm.getMessage("messages.rewards.already-claimed-error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            if (isPremium) {
                if (!data.claimedPremiumRewards.contains(level)) {
                    data.claimedPremiumRewards.add(level);
                    plugin.getPlayerDataManager().markForSave(player.getUniqueId());
                }
            } else {
                if (!data.claimedFreeRewards.contains(level)) {
                    data.claimedFreeRewards.add(level);
                    plugin.getPlayerDataManager().markForSave(player.getUniqueId());
                }
            }
            return;
        }

        if (isPremium) {
            data.claimedPremiumRewards.add(level);
        } else {
            data.claimedFreeRewards.add(level);
        }

        MessageManager messageManager = plugin.getMessageManager();
        StringBuilder message = new StringBuilder(messageManager.getPrefix() + messageManager.getMessage(
                isPremium ? "messages.rewards.premium-claimed" : "messages.rewards.free-claimed"));
        FileConfiguration config = isPremium ?
                configManager.getBattlePassPremiumConfig() :
                configManager.getBattlePassFreeConfig();
        String levelPath = "level-" + level;

        for (Reward reward : rewards) {
            if (reward.command != null) {
                String command = reward.command.replace("<player>", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                message.append("\n").append(messageManager.getMessage("messages.rewards.command-reward",
                        "%reward%", reward.displayName));
            } else {
                ItemStack item = null;
                if (config.contains(levelPath)) {
                    ConfigurationSection levelSection = config.getConfigurationSection(levelPath);
                    if (levelSection != null) {
                        if (levelSection.contains("serialized-item")) {
                            item = ItemSerializer.loadItemFromConfig(levelSection);
                        } else if (levelSection.contains("items")) {
                            ConfigurationSection itemsSection = levelSection.getConfigurationSection("items");
                            if (itemsSection != null) {
                                for (String key : itemsSection.getKeys(false)) {
                                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                                    if (itemSection != null && !itemSection.contains("command")) {
                                        ItemStack loadedItem = ItemSerializer.loadItemFromConfig(itemSection);
                                        if (loadedItem != null && loadedItem.getType() == reward.material) {
                                            item = loadedItem;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (item == null) {
                    item = new ItemStack(reward.material, reward.amount);
                }

                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                String itemName = reward.displayName;

                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    message.append("\n").append(messageManager.getMessage("messages.rewards.item-reward-dropped",
                            "%amount%", String.valueOf(item.getAmount()),
                            "%item%", itemName));
                } else {
                    message.append("\n").append(messageManager.getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(item.getAmount()),
                            "%item%", itemName));
                }
            }
        }

        player.sendMessage(message.toString());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());
    }

    public int countAvailableRewards(Player player, PlayerData data) {
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