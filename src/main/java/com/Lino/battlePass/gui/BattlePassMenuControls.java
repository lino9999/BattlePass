package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.SeasonRotationManager;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.GradientColorParser;
import com.Lino.battlePass.utils.DailyRewardCooldown;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

final class BattlePassMenuControls {

    private final BattlePass plugin;
    private final Player player;
    private final PlayerData playerData;
    private final int page;
    private final int maxLevel;

    BattlePassMenuControls(BattlePass plugin, Player player, PlayerData playerData, int page, int maxLevel) {
        this.plugin = plugin;
        this.player = player;
        this.playerData = playerData;
        this.page = page;
        this.maxLevel = maxLevel;
    }

    void setupProgressItem(Inventory gui) {
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.progress.name"));

        List<String> lore = new ArrayList<>();
        String premiumStatus = playerData.hasPremium ?
                plugin.getMessageManager().getMessage("items.premium-status.active") :
                plugin.getMessageManager().getMessage("items.premium-status.inactive");

        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        String currentSeason = rotation.isRotationEnabled() ?
                String.valueOf(rotation.getCurrentSeason()) : "1";

        String currentSeasonLine = plugin.getMessageManager().getMessagesConfig().getString(
                "items.progress.current-season",
                "&8▸ &7Current Season: <gradient:#FFD93D:#FF6B6B>%current_season%</gradient>");

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.progress.lore")) {
            String processedLine = line
                    .replace("%level%", String.valueOf(playerData.level))
                    .replace("%xp%", String.valueOf(playerData.xp))
                    .replace("%xp_needed%", String.valueOf(plugin.getConfigManager().getXpPerLevel()))
                    .replace("%max_level%", String.valueOf(maxLevel))
                    .replace("%premium_status%", premiumStatus)
                    .replace("%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd())
                    .replace("%current_season%", currentSeason);
            lore.add(GradientColorParser.parse(processedLine));

            if (line.contains("%season_time%") && currentSeasonLine != null && !currentSeasonLine.isEmpty()) {
                lore.add(GradientColorParser.parse(currentSeasonLine.replace("%current_season%", currentSeason)));
            }
        }

        meta.setLore(lore);
        info.setItemMeta(meta);
        gui.setItem(4, info);
    }

    void setupNavigationButtons(Inventory gui) {
        int maxPages = plugin.getRewardManager().getMaxPage();

        if (page > 1) {
            gui.setItem(36, createNavigationButton("previous", page - 1));
        }

        if (page < maxPages) {
            gui.setItem(44, createNavigationButton("next", page + 1));
        }
    }

    private ItemStack createNavigationButton(String direction, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        int navigationCustomModelData = plugin.getConfigManager().getGuiNavigationCustomModelData();
        if (navigationCustomModelData > 0) {
            meta.setCustomModelData(navigationCustomModelData);
        }

        if (direction.equals("previous")) {
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.previous-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.previous-page.lore")) {
                String processedLine = line.replace("%page%", String.valueOf(targetPage));
                lore.add(GradientColorParser.parse(processedLine));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    plugin.getEventManager().getNavigationKey(),
                    PersistentDataType.STRING,
                    "previous"
            );
        } else {
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.next-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.next-page.lore")) {
                String processedLine = line.replace("%page%", String.valueOf(targetPage));
                lore.add(GradientColorParser.parse(processedLine));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    plugin.getEventManager().getNavigationKey(),
                    PersistentDataType.STRING,
                    "next"
            );
        }

        item.setItemMeta(meta);
        return item;
    }

    void setupActionButtons(Inventory gui) {
        ItemStack missions = new ItemStack(Material.BOOK);
        ItemMeta missionsMeta = missions.getItemMeta();
        missionsMeta.setDisplayName(plugin.getMessageManager().getMessage("items.missions-button.name"));

        List<String> missionsLore = new ArrayList<>();
        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.missions-button.lore")) {
            String processedLine = line.replace("%reset_time%", plugin.getMissionManager().getTimeUntilReset());
            missionsLore.add(GradientColorParser.parse(processedLine));
        }
        missionsMeta.setLore(missionsLore);
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta leaderboardMeta = leaderboard.getItemMeta();
        leaderboardMeta.setDisplayName(plugin.getMessageManager().getMessage("items.leaderboard-button.name"));

        List<String> lboardLore = new ArrayList<>();
        String coinsTime = plugin.getCoinsDistributionTask() != null ?
                plugin.getCoinsDistributionTask().getTimeUntilNextDistribution() : "Unknown";

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.leaderboard-button.lore")) {
            String processedLine = line.replace("%coins_time%", coinsTime);
            lboardLore.add(GradientColorParser.parse(processedLine));
        }
        leaderboardMeta.setLore(lboardLore);
        leaderboard.setItemMeta(leaderboardMeta);
        gui.setItem(48, leaderboard);

        if (plugin.getConfigManager().isShopEnabled()) {
            ItemStack shop = new ItemStack(Material.GOLD_INGOT);
            ItemMeta shopMeta = shop.getItemMeta();
            shopMeta.setDisplayName(plugin.getMessageManager().getMessage("items.shop-button.name"));

            List<String> shopLore = new ArrayList<>();
            for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.shop-button.lore")) {
                String processedLine = line.replace("%coins%", String.valueOf(playerData.battleCoins));
                shopLore.add(GradientColorParser.parse(processedLine));
            }
            shopMeta.setLore(shopLore);
            shop.setItemMeta(shopMeta);
            gui.setItem(47, shop);
        }

        ItemStack dailyReward = new ItemStack(Material.SUNFLOWER);
        ItemMeta dailyMeta = dailyReward.getItemMeta();
        dailyMeta.setDisplayName(plugin.getMessageManager().getMessage("items.daily-reward.name"));

        List<String> dailyLore = new ArrayList<>();
        boolean canClaim = DailyRewardCooldown.isAvailable(playerData.lastDailyReward, System.currentTimeMillis());
        String timeUntil = plugin.getMissionManager().getTimeUntilDailyReward(playerData.lastDailyReward);

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(
                canClaim ? "items.daily-reward.lore-available" : "items.daily-reward.lore-cooldown")) {
            String processedLine = line
                    .replace("%xp%", String.valueOf(plugin.getConfigManager().getDailyRewardXP()))
                    .replace("%time%", timeUntil);
            dailyLore.add(GradientColorParser.parse(processedLine));
        }
        dailyMeta.setLore(dailyLore);
        dailyReward.setItemMeta(dailyMeta);
        gui.setItem(50, dailyReward);

        if (player.hasPermission("battlepass.admin")) {
            ItemStack editRewards = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta editMeta = editRewards.getItemMeta();
            editMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚙ Edit Rewards</gradient>"));
            List<String> editLore = new ArrayList<>();
            editLore.add("");
            editLore.add(GradientColorParser.parse("&7Open the rewards editor"));
            editLore.add(GradientColorParser.parse("&7to modify battle pass rewards"));
            editLore.add("");
            editLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▶ CLICK TO EDIT</gradient>"));
            editMeta.setLore(editLore);
            editRewards.setItemMeta(editMeta);
            gui.setItem(46, editRewards);
        }
    }
}
