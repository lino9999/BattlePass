package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.SeasonRotationManager;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BattlePassGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;
    private final int page;
    private final int maxLevel;

    public BattlePassGui(BattlePass plugin, Player player, int page) {
        super(plugin, plugin.getMessageManager().getGuiMessage("gui.battlepass", "%page%", String.valueOf(page)), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        this.page = page;
        this.maxLevel = plugin.getRewardManager().getMaxLevel();
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
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.progress.name"));

        String premiumStatus = playerData.hasPremium
                ? plugin.getMessageManager().getGuiMessage("items.premium-status.active")
                : plugin.getMessageManager().getGuiMessage("items.premium-status.inactive");

        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        String currentSeason = rotation.isRotationEnabled() ? String.valueOf(rotation.getCurrentSeason()) : "1";

        meta.setLore(plugin.getMessageManager().getGuiMessages("items.progress.lore",
                "%level%", String.valueOf(playerData.level),
                "%xp%", String.valueOf(playerData.xp),
                "%xp_needed%", String.valueOf(plugin.getConfigManager().getXpPerLevel()),
                "%premium_status%", premiumStatus,
                "%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd(),
                "%current_season%", currentSeason));
        info.setItemMeta(meta);
        gui.setItem(4, info);
    }

    private void setupRewards(Inventory gui) {
        int startLevel = (page - 1) * 9 + 1;
        Map<Integer, List<Reward>> premiumRewards = plugin.getRewardManager().getPremiumRewardsByLevel();
        Map<Integer, List<Reward>> freeRewards = plugin.getRewardManager().getFreeRewardsByLevel();

        for (int i = 0; i <= 8; i++) {
            int level = startLevel + i;
            if (level > maxLevel) {
                break;
            }

            List<Reward> premiumLevel = premiumRewards.get(level);
            if (premiumLevel != null && !premiumLevel.isEmpty()) {
                if (!playerData.claimedPremiumRewards.contains(level)) {
                    gui.setItem(9 + i, createRewardItem(premiumLevel, level, playerData, playerData.hasPremium, true));
                } else if (!plugin.getConfigManager().shouldHidePremiumClaimedRewards()) {
                    ItemStack claimed = createClaimedRewardItem(premiumLevel, level, true);
                    if (claimed != null) {
                        gui.setItem(9 + i, claimed);
                    }
                }
            }

            List<Reward> freeLevel = freeRewards.get(level);
            if (freeLevel != null && !freeLevel.isEmpty()) {
                if (!playerData.claimedFreeRewards.contains(level)) {
                    gui.setItem(27 + i, createRewardItem(freeLevel, level, playerData, true, false));
                } else if (!plugin.getConfigManager().shouldHideFreeClaimedRewards()) {
                    ItemStack claimed = createClaimedRewardItem(freeLevel, level, false);
                    if (claimed != null) {
                        gui.setItem(27 + i, claimed);
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
            meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.reward-available.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));
            meta.setLore(createRewardLore(rewards, "items.reward-available"));
            item.setItemMeta(meta);
            return item;
        }

        if (!hasAccess && isPremium) {
            ItemStack item = new ItemStack(plugin.getConfigManager().getGuiPremiumNoPassMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(level)));
            meta.setLore(createRewardLore(rewards, "items.reward-premium-locked"));
            item.setItemMeta(meta);
            return item;
        }

        Material lockedMaterial = isPremium
                ? plugin.getConfigManager().getGuiPremiumLockedMaterial()
                : plugin.getConfigManager().getGuiFreeLockedMaterial();

        ItemStack item = new ItemStack(lockedMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.reward-level-locked.name",
                "%level%", String.valueOf(level),
                "%type%", rewardType));
        meta.setLore(createRewardLore(rewards, "items.reward-level-locked"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClaimedRewardItem(List<Reward> rewards, int level, boolean isPremium) {
        Material material = isPremium
                ? plugin.getConfigManager().getGuiPremiumClaimedMaterial()
                : plugin.getConfigManager().getGuiFreeClaimedMaterial();

        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.reward-claimed.name",
                "%level%", String.valueOf(level),
                "%type%", plugin.getMessageManager().getMessage(isPremium ? "reward-types.premium" : "reward-types.free")));
        meta.setLore(createRewardLore(rewards, "items.reward-claimed"));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> createRewardLore(List<Reward> rewards, String configPath) {
        List<String> lore = new ArrayList<>();
        lore.addAll(plugin.getMessageManager().getGuiMessages(configPath + ".lore-header"));

        for (Reward reward : rewards) {
            lore.add(plugin.getMessageManager().getMessage("messages.rewards.command-reward", "%reward%", reward.displayName));
        }

        lore.addAll(plugin.getMessageManager().getGuiMessages(configPath + ".lore-footer",
                "%level%", String.valueOf(rewards.get(0).level),
                "%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd()));
        return lore;
    }

    private void setupSeparator(Inventory gui) {
        ItemStack separator = new ItemStack(plugin.getConfigManager().getGuiSeparatorMaterial());
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.separator.name"));
        separator.setItemMeta(meta);

        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }
    }

    private void setupNavigationButtons(Inventory gui) {
        int maxPages = (int) Math.ceil(maxLevel / 9.0);
        if (maxPages < 1) {
            maxPages = 1;
        }

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

        if ("previous".equals(direction)) {
            meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.previous-page.name"));
            meta.setLore(plugin.getMessageManager().getGuiMessages("items.previous-page.lore",
                    "%page%", String.valueOf(targetPage)));
            meta.getPersistentDataContainer().set(plugin.getEventManager().getNavigationKey(), PersistentDataType.STRING, "previous");
        } else {
            meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.next-page.name"));
            meta.setLore(plugin.getMessageManager().getGuiMessages("items.next-page.lore",
                    "%page%", String.valueOf(targetPage)));
            meta.getPersistentDataContainer().set(plugin.getEventManager().getNavigationKey(), PersistentDataType.STRING, "next");
        }

        item.setItemMeta(meta);
        return item;
    }

    private void setupActionButtons(Inventory gui) {
        ItemStack missions = new ItemStack(Material.BOOK);
        ItemMeta missionsMeta = missions.getItemMeta();
        missionsMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.missions-button.name"));
        missionsMeta.setLore(plugin.getMessageManager().getGuiMessages("items.missions-button.lore",
                "%reset_time%", plugin.getMissionManager().getTimeUntilReset()));
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta leaderboardMeta = leaderboard.getItemMeta();
        leaderboardMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.leaderboard-button.name"));
        String coinsTime = plugin.getCoinsDistributionTask() != null
                ? plugin.getCoinsDistributionTask().getTimeUntilNextDistribution()
                : plugin.getMessageManager().getMessage("time.unknown");
        leaderboardMeta.setLore(plugin.getMessageManager().getGuiMessages("items.leaderboard-button.lore",
                "%coins_time%", coinsTime));
        leaderboard.setItemMeta(leaderboardMeta);
        gui.setItem(48, leaderboard);

        if (plugin.getConfigManager().isShopEnabled()) {
            ItemStack shop = new ItemStack(Material.GOLD_INGOT);
            ItemMeta shopMeta = shop.getItemMeta();
            shopMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.shop-button.name"));
            shopMeta.setLore(plugin.getMessageManager().getGuiMessages("items.shop-button.lore",
                    "%coins%", String.valueOf(playerData.battleCoins)));
            shop.setItemMeta(shopMeta);
            gui.setItem(47, shop);
        }

        ItemStack dailyReward = new ItemStack(Material.SUNFLOWER);
        ItemMeta dailyMeta = dailyReward.getItemMeta();
        dailyMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.daily-reward.name"));

        boolean canClaim = System.currentTimeMillis() - playerData.lastDailyReward >= 24L * 60L * 60L * 1000L;
        String timeUntil = plugin.getMissionManager().getTimeUntilDailyReward(playerData.lastDailyReward);
        dailyMeta.setLore(plugin.getMessageManager().getGuiMessages(
                canClaim ? "items.daily-reward.lore-available" : "items.daily-reward.lore-cooldown",
                "%xp%", String.valueOf(plugin.getConfigManager().getDailyRewardXP()),
                "%time%", timeUntil));
        dailyReward.setItemMeta(dailyMeta);
        gui.setItem(50, dailyReward);

        if (player.hasPermission("battlepass.admin")) {
            ItemStack editRewards = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta editMeta = editRewards.getItemMeta();
            editMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.admin-rewards-editor.name"));
            editMeta.setLore(plugin.getMessageManager().getGuiMessages("items.admin-rewards-editor.lore"));
            editRewards.setItemMeta(editMeta);
            gui.setItem(46, editRewards);
        }
    }
}
