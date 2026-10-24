package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.GradientColorParser;
import com.Lino.battlePass.utils.Pagination;
import com.Lino.battlePass.utils.ConfigurationFiles;
import com.Lino.battlePass.utils.RewardConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
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

        int editingSeason = plugin.getRewardEditorManager().getSeasonEditingContext(player.getUniqueId());
        String seasonLabel = editingSeason > 0 ? " &8[&eSeason " + editingSeason + "&8]" : "";

        Map<Integer, List<Reward>> rewardsByLevel;

        if (editingSeason > 0) {
            try { rewardsByLevel = loadSeasonRewards(editingSeason, isPremium); }
            catch (RuntimeException failure) {
                plugin.getLogger().warning("Could not open season reward category: " + failure.getMessage());
                player.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage("messages.season.archive-unavailable"));
                return;
            }
        } else {
            rewardsByLevel = isPremium ?
                    plugin.getRewardManager().getPremiumRewardsByLevel() :
                    plugin.getRewardManager().getFreeRewardsByLevel();
        }

        int maxLevel = editingSeason > 0 ? rewardsByLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(54)
                : plugin.getRewardManager().getMaxLevel();
        int maxPages = Pagination.rewardEditorPages(maxLevel);
        int page = Math.max(1, Math.min(this.page, maxPages));
        String title = isPremium ?
                GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>★ Premium Rewards</gradient>" + seasonLabel + " &8- &7Page " + page) :
                GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>⚡ Free Rewards</gradient>" + seasonLabel + " &8- &7Page " + page);

        Inventory gui = BaseGui.createInventory(54, title, BaseHolder.MenuContext.rewards(page, isPremium));

        for (int slot = 0; slot < LEVELS_PER_PAGE; slot++) {
            int level = Pagination.levelAt(page, LEVELS_PER_PAGE, slot);
            if (level == 0) break;
            gui.setItem(slot, createLevelChest(level, rewardsByLevel.get(level)));
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

        if (page < maxPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Next Page →</gradient>"));
            List<String> nextLore = new ArrayList<>();
            nextLore.add(GradientColorParser.parse("&7Go to page " + (page + 1)));
            nextMeta.setLore(nextLore);
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(GradientColorParser.parse("<gradient:#FF6B6B:#FF0000>← Back</gradient>"));
        List<String> backLore = new ArrayList<>();
        backLore.add(GradientColorParser.parse("&7Return to category selection"));
        backMeta.setLore(backLore);
        back.setItemMeta(backMeta);
        gui.setItem(48, back);

        ItemStack save = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = save.getItemMeta();
        saveMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>✓ Save & Apply All</gradient>"));
        List<String> saveLore = new ArrayList<>();
        saveLore.add(GradientColorParser.parse("&7Save all changes and reload"));
        saveMeta.setLore(saveLore);
        save.setItemMeta(saveMeta);
        gui.setItem(49, save);

        player.openInventory(gui);
    }

    private Map<Integer, List<Reward>> loadSeasonRewards(int seasonNumber, boolean premium) {
        Map<Integer, List<Reward>> rewardsByLevel = new HashMap<>();

        plugin.getSeasonRotationManager().prepareSeasonArchive(seasonNumber);
        File file = plugin.getSeasonRotationManager().getSeasonRewardFile(seasonNumber, premium);
        if (!file.exists()) return rewardsByLevel;

        FileConfiguration config = ConfigurationFiles.read(file);
        return RewardConfiguration.readRuntimeRewards(config, !premium, plugin.getLogger()::warning);
    }

    private ItemStack createLevelChest(int level, List<Reward> rewards) {
        boolean hasRewards = rewards != null && !rewards.isEmpty();

        Material material = hasRewards ? Material.CHEST : Material.GLASS;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(GradientColorParser.parse(
                (hasRewards ? "<gradient:#00FF88:#45B7D1>" : "&7") +
                        "Level " + level +
                        (hasRewards ? "</gradient>" : "")));

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (hasRewards) {
            lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Current Rewards:</gradient>"));
            for (Reward reward : rewards) {
                lore.add(GradientColorParser.parse("&7• &f" + reward.displayName));
            }
        } else {
            lore.add(GradientColorParser.parse("&7No rewards configured"));
        }

        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▶ CLICK TO EDIT</gradient>"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
