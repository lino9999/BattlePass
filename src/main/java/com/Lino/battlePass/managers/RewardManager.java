package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RewardManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;

    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private final Map<Integer, List<Reward>> freeRewardsByLevel = new HashMap<>();
    private final Map<Integer, List<Reward>> premiumRewardsByLevel = new HashMap<>();

    public RewardManager(BattlePass plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadRewards() {
        freeRewards.clear();
        premiumRewards.clear();
        freeRewardsByLevel.clear();
        premiumRewardsByLevel.clear();

        FileConfiguration config = configManager.getConfig();

        for (int i = 1; i <= 54; i++) {
            String freePath = "rewards.free.level-" + i;
            String premiumPath = "rewards.premium.level-" + i;

            List<Reward> freeLevel = new ArrayList<>();
            List<Reward> premiumLevel = new ArrayList<>();

            if (config.contains(freePath)) {
                if (config.contains(freePath + ".material") || config.contains(freePath + ".command")) {
                    Reward reward = loadSingleReward(config, freePath, i, true);
                    if (reward != null) {
                        freeRewards.add(reward);
                        freeLevel.add(reward);
                    }
                } else if (config.contains(freePath + ".items")) {
                    for (String key : config.getConfigurationSection(freePath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(config, freePath + ".items." + key, i, true);
                        if (reward != null) {
                            freeRewards.add(reward);
                            freeLevel.add(reward);
                        }
                    }
                }
            }

            if (config.contains(premiumPath)) {
                if (config.contains(premiumPath + ".material") || config.contains(premiumPath + ".command")) {
                    Reward reward = loadSingleReward(config, premiumPath, i, false);
                    if (reward != null) {
                        premiumRewards.add(reward);
                        premiumLevel.add(reward);
                    }
                } else if (config.contains(premiumPath + ".items")) {
                    for (String key : config.getConfigurationSection(premiumPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(config, premiumPath + ".items." + key, i, false);
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
                return new Reward(level, mat, amount, isFree);
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
        if (isPremium) {
            data.claimedPremiumRewards.add(level);
        } else {
            data.claimedFreeRewards.add(level);
        }

        MessageManager messageManager = plugin.getMessageManager();
        StringBuilder message = new StringBuilder(messageManager.getPrefix() + messageManager.getMessage(
                isPremium ? "messages.rewards.premium-claimed" : "messages.rewards.free-claimed"));

        for (Reward reward : rewards) {
            if (reward.command != null) {
                String command = reward.command.replace("<player>", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                message.append("\n").append(messageManager.getMessage("messages.rewards.command-reward",
                        "%reward%", reward.displayName));
            } else {
                player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                message.append("\n").append(messageManager.getMessage("messages.rewards.item-reward",
                        "%amount%", String.valueOf(reward.amount),
                        "%item%", formatMaterial(reward.material)));
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
}