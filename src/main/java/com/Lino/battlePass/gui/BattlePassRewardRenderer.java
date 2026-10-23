package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.GradientColorParser;
import com.Lino.battlePass.utils.Pagination;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BattlePassRewardRenderer {

    private final BattlePass plugin;
    private final PlayerData playerData;
    private final int page;
    private final int maxLevel;

    BattlePassRewardRenderer(BattlePass plugin, PlayerData playerData, int page, int maxLevel) {
        this.plugin = plugin;
        this.playerData = playerData;
        this.page = page;
        this.maxLevel = maxLevel;
    }

    void setupRewards(Inventory gui) {
        Map<Integer, List<Reward>> premiumRewards = plugin.getRewardManager().getPremiumRewardsByLevel();
        Map<Integer, List<Reward>> freeRewards = plugin.getRewardManager().getFreeRewardsByLevel();

        for (int i = 0; i <= 8; i++) {
            int level = Pagination.levelAt(page, 9, i);
            if (level == 0 || level > maxLevel) break;

            List<Reward> premiumLevel = premiumRewards.get(level);
            if (premiumLevel != null && !premiumLevel.isEmpty()) {
                if (!playerData.claimedPremiumRewards.contains(level)) {
                    ItemStack premiumItem = createRewardItem(premiumLevel, level, playerData, playerData.hasPremium, true);
                    gui.setItem(9 + i, premiumItem);
                } else {
                    if (!plugin.getConfigManager().shouldHidePremiumClaimedRewards()) {
                        ItemStack claimedItem = createClaimedRewardItem(premiumLevel, level, true);
                        if (claimedItem != null) {
                            gui.setItem(9 + i, claimedItem);
                        }
                    }
                }
            }

            List<Reward> freeLevel = freeRewards.get(level);
            if (freeLevel != null && !freeLevel.isEmpty()) {
                if (!playerData.claimedFreeRewards.contains(level)) {
                    ItemStack freeItem = createRewardItem(freeLevel, level, playerData, true, false);
                    gui.setItem(27 + i, freeItem);
                } else {
                    if (!plugin.getConfigManager().shouldHideFreeClaimedRewards()) {
                        ItemStack claimedItem = createClaimedRewardItem(freeLevel, level, false);
                        if (claimedItem != null) {
                            gui.setItem(27 + i, claimedItem);
                        }
                    }
                }
            }
        }
    }

    private ItemStack createRewardItem(List<Reward> rewards, int level, PlayerData data, boolean hasAccess, boolean isPremium) {
        String rewardType = plugin.getMessageManager().getMessage(isPremium ? "reward-types.premium" : "reward-types.free");

        if (data.level >= level && hasAccess) {
            ItemStack item = new ItemStack(plugin.getConfigManager().getGuiRewardAvailableMaterial());
            ItemMeta meta = item.getItemMeta();
            applyGuiCustomModelData(meta, "gui.reward-available");
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-available.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-available");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;

        } else if (!hasAccess && isPremium) {
            ItemStack item = new ItemStack(plugin.getConfigManager().getGuiPremiumNoPassMaterial());
            ItemMeta meta = item.getItemMeta();
            applyGuiCustomModelData(meta, "gui.premium-no-pass");
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(level)));

            List<String> lore = createRewardLore(rewards, "items.reward-premium-locked");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;

        } else {
            Material lockedMaterial = isPremium ?
                    plugin.getConfigManager().getGuiPremiumLockedMaterial() :
                    plugin.getConfigManager().getGuiFreeLockedMaterial();

            ItemStack item = new ItemStack(lockedMaterial);
            ItemMeta meta = item.getItemMeta();
            applyGuiCustomModelData(meta, isPremium ? "gui.reward-locked.premium" : "gui.reward-locked.free");
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-level-locked.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-level-locked");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
    }

    private void applyGuiCustomModelData(ItemMeta meta, String configPath) {
        Integer customModelData = plugin.getConfigManager().getGuiCustomModelData(configPath);
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
    }

    private ItemStack createClaimedRewardItem(List<Reward> rewards, int level, boolean isPremium) {
        Material material = isPremium ?
                plugin.getConfigManager().getGuiPremiumClaimedMaterial() :
                plugin.getConfigManager().getGuiFreeClaimedMaterial();

        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        applyGuiCustomModelData(meta, isPremium ? "gui.reward-claimed.premium" : "gui.reward-claimed.free");
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-claimed.name",
                "%level%", String.valueOf(level),
                "%type%", plugin.getMessageManager().getMessage(isPremium ? "reward-types.premium" : "reward-types.free")));

        List<String> lore = createRewardLore(rewards, "items.reward-claimed");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> createRewardLore(List<Reward> rewards, String configPath) {
        List<String> lore = new ArrayList<>();

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(configPath + ".lore-header")) {
            lore.add(GradientColorParser.parse(line));
        }

        for (Reward r : rewards) {
            lore.add(plugin.getMessageManager().getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
        }

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(configPath + ".lore-footer")) {
            String processedLine = line
                    .replace("%level%", String.valueOf(rewards.get(0).level))
                    .replace("%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd());
            lore.add(GradientColorParser.parse(processedLine));
        }

        return lore;
    }

    void setupSeparator(Inventory gui) {
        ItemStack separator = new ItemStack(plugin.getConfigManager().getGuiSeparatorMaterial());
        ItemMeta meta = separator.getItemMeta();
        applyGuiCustomModelData(meta, "gui.separator");
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.separator.name"));
        separator.setItemMeta(meta);

        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }
    }
}
