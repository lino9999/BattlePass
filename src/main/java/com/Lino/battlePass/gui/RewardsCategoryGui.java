package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RewardsCategoryGui {

    private final BattlePass plugin;
    private final Player player;
    private final boolean isPremium;
    private final int page;
    private static final int LEVELS_PER_PAGE = 45;

    public RewardsCategoryGui(BattlePass plugin, Player player, boolean isPremium, int page) {
        this.plugin = plugin;
        this.player = player;
        this.isPremium = isPremium;
        this.page = page;
    }

    public void open() {
        if (!player.hasPermission("battlepass.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>✗ You don't have permission to access this!</gradient>"));
            return;
        }

        String title = isPremium ?
                GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>★ Premium Rewards</gradient> &8- &7Page " + page) :
                GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>⚡ Free Rewards</gradient> &8- &7Page " + page);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int startLevel = (page - 1) * LEVELS_PER_PAGE + 1;
        int endLevel = startLevel + LEVELS_PER_PAGE - 1;

        Map<Integer, List<Reward>> rewardsByLevel = isPremium ?
                plugin.getRewardManager().getPremiumRewardsByLevel() :
                plugin.getRewardManager().getFreeRewardsByLevel();

        int slot = 0;
        for (int level = startLevel; level <= endLevel && slot < 45; level++) {
            ItemStack chest = createLevelChest(level, rewardsByLevel.get(level));
            gui.setItem(slot, chest);
            slot++;
        }

        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>← Previous Page</gradient>"));
            List<String> prevLore = new ArrayList<>();
            prevLore.add(GradientColorParser.parse("&7Go to page " + (page - 1)));
            prevMeta.setLore(prevLore);
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        int maxLevel = plugin.getRewardManager().getMaxLevel();
        // Allow next page if current page is full OR if we are within range to add new levels
        // Basically, always allow one page further than the current max level
        if (endLevel < maxLevel + LEVELS_PER_PAGE) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Next Page →</gradient>"));
            List<String> nextLore = new ArrayList<>();
            nextLore.add(GradientColorParser.parse("&7Go to page " + (page + 1)));
            nextMeta.setLore(nextLore);
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack saveButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveButton.getItemMeta();
        saveMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>✓ Save All Changes</gradient>"));
        List<String> saveLore = new ArrayList<>();
        saveLore.add("");
        saveLore.add(GradientColorParser.parse("&7Save all reward changes"));
        saveLore.add(GradientColorParser.parse("&7and reload the plugin"));
        saveLore.add("");
        saveLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚠ Warning:</gradient>"));
        saveLore.add(GradientColorParser.parse("&7This will reload the entire plugin"));
        saveLore.add("");
        saveLore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>▶ CLICK TO SAVE</gradient>"));
        saveMeta.setLore(saveLore);
        saveButton.setItemMeta(saveMeta);
        gui.setItem(49, saveButton);

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(GradientColorParser.parse("<gradient:#FF6B6B:#FF0000>← Back</gradient>"));
        List<String> backLore = new ArrayList<>();
        backLore.add(GradientColorParser.parse("&7Return to Rewards Editor"));
        backMeta.setLore(backLore);
        backButton.setItemMeta(backMeta);
        gui.setItem(48, backButton);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFE66D:#FF6B6B>ℹ Instructions</gradient>"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("&7Click on any chest to edit"));
        infoLore.add(GradientColorParser.parse("&7rewards for that level"));
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("&8• &aGreen = Has rewards"));
        infoLore.add(GradientColorParser.parse("&8• &cRed = No rewards"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(50, info);

        player.openInventory(gui);
    }

    private ItemStack createLevelChest(int level, List<Reward> rewards) {
        boolean hasRewards = rewards != null && !rewards.isEmpty();
        Material material = hasRewards ? Material.LIME_SHULKER_BOX : Material.RED_SHULKER_BOX;

        ItemStack chest = new ItemStack(material);
        ItemMeta meta = chest.getItemMeta();

        String gradient = hasRewards ?
                "<gradient:#00FF88:#45B7D1>" :
                "<gradient:#FF6B6B:#FF0000>";

        meta.setDisplayName(GradientColorParser.parse(gradient + "Level " + level + "</gradient>"));

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (hasRewards) {
            lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Current Rewards:</gradient>"));
            for (Reward reward : rewards) {
                if (reward.command != null) {
                    lore.add(GradientColorParser.parse("&7• &eCommand: &f" + reward.displayName));
                } else {
                    lore.add(GradientColorParser.parse("&7• &f" + reward.amount + "x " + formatMaterial(reward.material)));
                }
            }
        } else {
            lore.add(GradientColorParser.parse("&7No rewards configured"));
        }

        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▶ CLICK TO EDIT</gradient>"));

        meta.setLore(lore);
        chest.setItemMeta(meta);

        return chest;
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
}