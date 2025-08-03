package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class BattlePassGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;
    private final int page;

    public BattlePassGui(BattlePass plugin, Player player, int page) {
        super(plugin, plugin.getMessageManager().getMessage("gui.battlepass", "%page%", String.valueOf(page)), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        this.page = page;
    }

    public void open() {
        Inventory gui = createInventory();

        setupProgressItem(gui);
        setupRewards(gui);
        setupSeparator(gui);
        setupNavigationButtons(gui);
        setupActionButtons(gui);

        player.openInventory(gui);
        plugin.getGuiManager().getCurrentPages().put(player.getEntityId(), page);
    }

    private void setupProgressItem(Inventory gui) {
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.progress.name"));

        List<String> lore = new ArrayList<>();
        String premiumStatus = playerData.hasPremium ?
                plugin.getMessageManager().getMessage("items.premium-status.active") :
                plugin.getMessageManager().getMessage("items.premium-status.inactive");

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.progress.lore")) {
            String processedLine = line
                    .replace("%level%", String.valueOf(playerData.level))
                    .replace("%xp%", String.valueOf(playerData.xp))
                    .replace("%xp_needed%", String.valueOf(plugin.getConfigManager().getXpPerLevel()))
                    .replace("%premium_status%", premiumStatus)
                    .replace("%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd());
            lore.add(GradientColorParser.parse(processedLine));
        }

        meta.setLore(lore);
        info.setItemMeta(meta);
        gui.setItem(4, info);
    }

    private void setupRewards(Inventory gui) {
        int startLevel = (page - 1) * 9 + 1;
        Map<Integer, List<Reward>> premiumRewards = plugin.getRewardManager().getPremiumRewardsByLevel();
        Map<Integer, List<Reward>> freeRewards = plugin.getRewardManager().getFreeRewardsByLevel();

        for (int i = 0; i <= 8 && startLevel + i <= 54; i++) {
            int level = startLevel + i;

            // Premium rewards (top row)
            List<Reward> premiumLevel = premiumRewards.get(level);
            if (premiumLevel != null && !premiumLevel.isEmpty()) {
                if (!playerData.claimedPremiumRewards.contains(level)) {
                    ItemStack premiumItem = createRewardItem(premiumLevel, level, playerData, playerData.hasPremium, true);
                    gui.setItem(9 + i, premiumItem);
                }
                // If claimed, leave the slot empty
            }

            // Free rewards (bottom row)
            List<Reward> freeLevel = freeRewards.get(level);
            if (freeLevel != null && !freeLevel.isEmpty()) {
                if (!playerData.claimedFreeRewards.contains(level)) {
                    ItemStack freeItem = createRewardItem(freeLevel, level, playerData, true, false);
                    gui.setItem(27 + i, freeItem);
                }
                // If claimed, leave the slot empty
            }
        }
    }

    private ItemStack createRewardItem(List<Reward> rewards, int level, PlayerData data, boolean hasAccess, boolean isPremium) {
        String rewardType = plugin.getMessageManager().getMessage(isPremium ? "reward-types.premium" : "reward-types.free");

        if (data.level >= level && hasAccess) {
            // Can claim
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-available.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-available");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;

        } else if (!hasAccess && isPremium) {
            // Premium locked
            ItemStack item = new ItemStack(Material.IRON_BARS);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(level)));

            List<String> lore = createRewardLore(rewards, "items.reward-premium-locked");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;

        } else {
            // Level locked
            ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getMessageManager().getMessage("items.reward-level-locked.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-level-locked");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
    }

    private List<String> createRewardLore(List<Reward> rewards, String configPath) {
        List<String> lore = new ArrayList<>();

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(configPath + ".lore-header")) {
            lore.add(GradientColorParser.parse(line));
        }

        for (Reward r : rewards) {
            if (r.command != null) {
                lore.add(plugin.getMessageManager().getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
            } else {
                lore.add(plugin.getMessageManager().getMessage("messages.rewards.item-reward",
                        "%amount%", String.valueOf(r.amount),
                        "%item%", formatMaterial(r.material)));
            }
        }

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(configPath + ".lore-footer")) {
            String processedLine = line
                    .replace("%level%", String.valueOf(rewards.get(0).level))
                    .replace("%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd());
            lore.add(GradientColorParser.parse(processedLine));
        }

        return lore;
    }

    private void setupSeparator(Inventory gui) {
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.separator.name"));
        separator.setItemMeta(meta);

        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }
    }

    private void setupNavigationButtons(Inventory gui) {
        if (page > 1) {
            gui.setItem(45, createNavigationItem(false, page - 1));
        }

        if (page < 6) {
            gui.setItem(53, createNavigationItem(true, page + 1));
        }
    }

    private ItemStack createNavigationItem(boolean next, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (next) {
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
        } else {
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
        }

        item.setItemMeta(meta);
        return item;
    }

    private void setupActionButtons(Inventory gui) {
        // Missions button
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

        // Leaderboard button
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

        // Shop button (if enabled)
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

        // Daily reward button
        ItemStack dailyReward = new ItemStack(Material.SUNFLOWER);
        ItemMeta dailyMeta = dailyReward.getItemMeta();
        dailyMeta.setDisplayName(plugin.getMessageManager().getMessage("items.daily-reward.name"));

        List<String> dailyLore = new ArrayList<>();
        boolean canClaim = System.currentTimeMillis() - playerData.lastDailyReward >= 24 * 60 * 60 * 1000;
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
    }
}