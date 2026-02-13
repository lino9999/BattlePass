package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

        String title = isPremium ?
                GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>★ Premium Rewards</gradient>" + seasonLabel + " &8- &7Page " + page) :
                GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>⚡ Free Rewards</gradient>" + seasonLabel + " &8- &7Page " + page);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int startLevel = (page - 1) * LEVELS_PER_PAGE + 1;
        int endLevel = startLevel + LEVELS_PER_PAGE - 1;

        Map<Integer, List<Reward>> rewardsByLevel;

        if (editingSeason > 0) {
            rewardsByLevel = loadSeasonRewards(editingSeason, isPremium);
        } else {
            rewardsByLevel = isPremium ?
                    plugin.getRewardManager().getPremiumRewardsByLevel() :
                    plugin.getRewardManager().getFreeRewardsByLevel();
        }

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

        File file = plugin.getSeasonRotationManager().getSeasonRewardFile(seasonNumber, premium);
        if (!file.exists()) return rewardsByLevel;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            if (!key.startsWith("level-")) continue;

            int level;
            try {
                level = Integer.parseInt(key.replace("level-", ""));
            } catch (NumberFormatException e) {
                continue;
            }

            List<Reward> rewards = new ArrayList<>();
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            if (section.contains("items")) {
                ConfigurationSection items = section.getConfigurationSection("items");
                if (items != null) {
                    for (String itemKey : items.getKeys(false)) {
                        ConfigurationSection itemSection = items.getConfigurationSection(itemKey);
                        if (itemSection != null && itemSection.contains("command")) {
                            String display = itemSection.getString("display", "Reward");
                            String cmd = itemSection.getString("command", "");
                            rewards.add(new Reward(level, cmd, display, premium));
                        } else if (itemSection != null) {
                            String material = itemSection.getString("material", "STONE");
                            String displayName = itemSection.getString("display-name", material);
                            rewards.add(new Reward(level, "", displayName, premium));
                        }
                    }
                }
            } else if (section.contains("command")) {
                String display = section.getString("display", "Reward");
                String cmd = section.getString("command", "");
                rewards.add(new Reward(level, cmd, display, premium));
            } else if (section.contains("material")) {
                String material = section.getString("material", "STONE");
                String displayName = section.getString("display-name", material);
                rewards.add(new Reward(level, "", displayName, premium));
            }

            if (!rewards.isEmpty()) {
                rewardsByLevel.put(level, rewards);
            }
        }

        return rewardsByLevel;
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