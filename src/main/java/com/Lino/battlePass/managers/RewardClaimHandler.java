package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RewardClaimHandler {

    private final BattlePass plugin;
    private final ConfigManager configManager;

    RewardClaimHandler(BattlePass plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    void claimRewards(Player player, PlayerData data, List<Reward> rewards,
                             int level, boolean isPremium) {

        MessageManager messages = plugin.getMessageManager();
        if (data == null) {
            player.sendMessage(messages.getPrefix() + messages.getMessage("messages.data-loading"));
            return;
        }
        if (level < 1 || data.level < level || (isPremium && !data.hasPremium) || rewards == null || rewards.isEmpty()) {
            player.sendMessage(messages.getPrefix() + messages.getMessage("messages.rewards.cannot-claim"));
            return;
        }
        Set<Integer> claimed = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;
        if (claimed.contains(level)) {
            player.sendMessage(messages.getPrefix() + messages.getMessage("messages.rewards.already-claimed-error"));
            return;
        }

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
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to execute reward command '" + reward.command + "': " + e.getMessage());
                }
                message.append("\n").append(messageManager.getMessage("messages.rewards.command-reward",
                        "%reward%", reward.displayName));
            } else {
                ItemStack item = null;
                if (reward.configurationPath != null) {
                    item = ItemSerializer.loadItemFromConfig(config.getConfigurationSection(reward.configurationPath));
                } else if (config.contains(levelPath)) {
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

                int amount = item.getAmount();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                String itemName = reward.displayName;

                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    message.append("\n").append(messageManager.getMessage("messages.rewards.item-reward-dropped",
                            "%amount%", String.valueOf(amount),
                            "%item%", itemName));
                } else {
                    message.append("\n").append(messageManager.getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(amount),
                            "%item%", itemName));
                }
            }
        }

        player.sendMessage(message.toString());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());
    }
}
