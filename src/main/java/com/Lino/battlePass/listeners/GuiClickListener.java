package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class GuiClickListener implements Listener {

    private final BattlePass plugin;

    public GuiClickListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        boolean isBattlePassGUI = false;
        for (int i = 1; i <= 6; i++) {
            if (title.equals(plugin.getMessageManager().getMessage("gui.battlepass", "%page%", String.valueOf(i)))) {
                isBattlePassGUI = true;
                break;
            }
        }

        if (!isBattlePassGUI && !title.equals(plugin.getMessageManager().getMessage("gui.leaderboard")) &&
                !title.equals(plugin.getMessageManager().getMessage("gui.missions")) &&
                !title.equals(plugin.getMessageManager().getMessage("gui.shop"))) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (isBattlePassGUI) {
            handleBattlePassClick(player, clicked, event.getSlot());
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.leaderboard"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                plugin.getGuiManager().openBattlePassGUI(player, page);
            }
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.missions"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                plugin.getGuiManager().openBattlePassGUI(player, page);
            }
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.shop"))) {
            handleShopClick(player, clicked, event.getSlot());
        }
    }

    private void handleBattlePassClick(Player player, ItemStack clicked, int slot) {
        int currentPage = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);

        if (clicked.getType() == Material.ARROW && clicked.hasItemMeta()) {
            var meta = clicked.getItemMeta();
            if (meta.getPersistentDataContainer().has(plugin.getEventManager().getNavigationKey(), PersistentDataType.STRING)) {
                String action = meta.getPersistentDataContainer().get(plugin.getEventManager().getNavigationKey(), PersistentDataType.STRING);
                if ("previous".equals(action) && currentPage > 1) {
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage - 1);
                } else if ("next".equals(action) && currentPage < 6) {
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage + 1);
                }
                return;
            }
        }

        switch (clicked.getType()) {
            case BOOK:
                plugin.getGuiManager().openMissionsGUI(player);
                break;

            case GOLDEN_HELMET:
                plugin.getGuiManager().openLeaderboardGUI(player);
                break;

            case GOLD_INGOT:
                plugin.getGuiManager().openShopGUI(player);
                break;

            case SUNFLOWER:
                handleDailyRewardClaim(player, currentPage);
                break;

            case COMMAND_BLOCK:
                if (player.hasPermission("battlepass.admin") && slot == 46) {
                    new com.Lino.battlePass.gui.RewardsEditorGui(plugin, player).open();
                }
                break;

            default:
                if (clicked.getType() == plugin.getConfigManager().getGuiRewardAvailableMaterial()) {
                    handleRewardClaim(player, slot, currentPage);
                }
                break;
        }
    }

    private void handleShopClick(Player player, ItemStack clicked, int slot) {
        if (clicked.getType() == Material.BARRIER) {
            int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
            plugin.getGuiManager().openBattlePassGUI(player, page);
            return;
        }

        if (slot == 4) return;

        plugin.getShopManager().purchaseItem(player, slot);
    }

    private void handleRewardClaim(Player player, int slot, int currentPage) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int startLevel = (currentPage - 1) * 9 + 1;

        if (slot >= 9 && slot <= 17) {
            int index = slot - 9;
            int level = startLevel + index;

            if (!data.hasPremium) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.premium.required"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            List<com.Lino.battlePass.models.Reward> levelRewards =
                    plugin.getRewardManager().getPremiumRewardsByLevel().get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                    plugin.getRewardManager().claimRewards(player, data, levelRewards, level, true);
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.rewards.cannot-claim"));
                }
            }

        } else if (slot >= 27 && slot <= 35) {
            int index = slot - 27;
            int level = startLevel + index;

            List<com.Lino.battlePass.models.Reward> levelRewards =
                    plugin.getRewardManager().getFreeRewardsByLevel().get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                    plugin.getRewardManager().claimRewards(player, data, levelRewards, level, false);
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.rewards.cannot-claim"));
                }
            }
        }
    }

    private void handleDailyRewardClaim(Player player, int currentPage) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long now = System.currentTimeMillis();
        long dayInMillis = 24 * 60 * 60 * 1000;

        if (now - data.lastDailyReward >= dayInMillis) {
            int xpReward = plugin.getConfigManager().getDailyRewardXP();
            data.xp += xpReward;
            data.lastDailyReward = now;

            int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
            boolean leveled = false;

            while (data.xp >= xpPerLevel && data.level < 54) {
                data.xp -= xpPerLevel;
                data.level++;
                data.totalLevels++;
                leveled = true;

                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.level-up",
                                "%level%", String.valueOf(data.level)));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                int available = plugin.getRewardManager().countAvailableRewards(player, data);
                if (available > 0) {
                    player.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.new-rewards"));
                }
            }

            plugin.getPlayerDataManager().markForSave(player.getUniqueId());

            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.daily-reward.claimed",
                            "%amount%", String.valueOf(xpReward)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            plugin.getGuiManager().openBattlePassGUI(player, currentPage);
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.daily-reward.already-claimed"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}