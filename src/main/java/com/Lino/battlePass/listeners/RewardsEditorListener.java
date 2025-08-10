package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.LevelRewardEditGui;
import com.Lino.battlePass.gui.RewardsCategoryGui;
import com.Lino.battlePass.gui.RewardsEditorGui;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

public class RewardsEditorListener implements Listener {

    private final BattlePass plugin;
    private static final String REWARDS_EDITOR_TITLE = "⚙ Rewards Editor";
    private static final String FREE_REWARDS_TITLE = "⚡ Free Rewards";
    private static final String PREMIUM_REWARDS_TITLE = "★ Premium Rewards";
    private static final String EDIT_LEVEL_TITLE = "Edit Level";

    public RewardsEditorListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        Player player = (Player) event.getWhoClicked();

        if (title == null) return;

        if (title.contains(REWARDS_EDITOR_TITLE)) {
            event.setCancelled(true);
            handleRewardsEditorClick(player, event.getSlot());
        } else if (title.contains(FREE_REWARDS_TITLE) || title.contains(PREMIUM_REWARDS_TITLE)) {
            event.setCancelled(true);
            handleRewardsCategoryClick(player, event.getSlot(), title.contains(PREMIUM_REWARDS_TITLE));
        } else if (title.contains(EDIT_LEVEL_TITLE)) {
            handleLevelEditClick(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title != null && title.contains(EDIT_LEVEL_TITLE)) {
            Player player = (Player) event.getWhoClicked();
            LevelRewardEditGui editor = plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId());

            if (editor != null) {
                for (Integer slot : event.getRawSlots()) {
                    if (slot >= 0 && slot < 36 && slot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title != null && title.contains(EDIT_LEVEL_TITLE)) {
            LevelRewardEditGui editor = plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId());
            if (editor != null && !plugin.getRewardEditorManager().hasCommandInput(player.getUniqueId())) {
                plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRewardEditorManager().hasCommandInput(player.getUniqueId())) {
            event.setCancelled(true);

            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getRewardEditorManager().handleCommandInput(player, message);
            });
        }
    }

    private void handleRewardsEditorClick(Player player, int slot) {
        plugin.getLogger().info("Rewards Editor Click - Slot: " + slot);

        switch (slot) {
            case 11:
                plugin.getLogger().info("Opening Free Rewards Category");
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RewardsCategoryGui(plugin, player, false, 1).open();
                }, 1L);
                break;
            case 15:
                plugin.getLogger().info("Opening Premium Rewards Category");
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RewardsCategoryGui(plugin, player, true, 1).open();
                }, 1L);
                break;
            case 22:
                plugin.getLogger().info("Going back to Battle Pass");
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getGuiManager().openBattlePassGUI(player, page);
                }, 1L);
                break;
            default:
                plugin.getLogger().info("Unknown slot clicked: " + slot);
                break;
        }
    }

    private void handleRewardsCategoryClick(Player player, int slot, boolean isPremium) {
        String title = ChatColor.stripColor(player.getOpenInventory().getTitle());
        int currentPage = 1;

        if (title != null && title.contains("Page")) {
            String pageStr = title.substring(title.lastIndexOf("Page") + 5).trim();
            try {
                currentPage = Integer.parseInt(pageStr);
            } catch (NumberFormatException ignored) {}
        }

        plugin.getLogger().info("Category Click - Slot: " + slot + ", Premium: " + isPremium + ", Page: " + currentPage);

        if (slot < 45) {
            int startLevel = (currentPage - 1) * 45 + 1;
            int level = startLevel + slot;

            if (level <= 54) {
                plugin.getLogger().info("Opening Level Editor for level " + level);
                player.closeInventory();
                final int finalLevel = level;
                final boolean finalIsPremium = isPremium;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getRewardEditorManager().openLevelEditor(player, finalLevel, finalIsPremium);
                }, 1L);
            }
        } else if (slot == 45 && currentPage > 1) {
            plugin.getLogger().info("Previous page");
            player.closeInventory();
            final int prevPage = currentPage - 1;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsCategoryGui(plugin, player, finalIsPremium, prevPage).open();
            }, 1L);
        } else if (slot == 53 && ((currentPage - 1) * 45 + 45) < 54) {
            plugin.getLogger().info("Next page");
            player.closeInventory();
            final int nextPage = currentPage + 1;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsCategoryGui(plugin, player, finalIsPremium, nextPage).open();
            }, 1L);
        } else if (slot == 48) {
            plugin.getLogger().info("Back to Rewards Editor");
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsEditorGui(plugin, player).open();
            }, 1L);
        } else if (slot == 49) {
            plugin.getLogger().info("Saving all rewards");
            player.closeInventory();
            plugin.getRewardEditorManager().saveAllRewards(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getGuiManager().openBattlePassGUI(player, 1);
            }, 20L);
        }
    }

    private void handleLevelEditClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        LevelRewardEditGui editor = plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId());

        if (editor == null) {
            event.setCancelled(true);
            return;
        }

        plugin.getLogger().info("Level Edit Click - Slot: " + slot);

        if (slot < 36) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                plugin.getLogger().info("Removing reward at slot " + slot);
                editor.removeReward(slot);
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    editor.open();
                }, 1L);
            } else if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                ItemStack item = event.getCursor().clone();
                plugin.getLogger().info("Adding item reward: " + item.getType());
                editor.addReward(new EditableReward(item));
                event.setCancelled(true);
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    editor.open();
                }, 1L);
            }
        } else if (slot >= 36 && slot < 54) {
            event.setCancelled(true);

            switch (slot) {
                case 46:
                    plugin.getLogger().info("Starting command input");
                    plugin.getRewardEditorManager().startCommandInput(player, editor.getLevel(), editor.isPremium());
                    break;
                case 48:
                    plugin.getLogger().info("Cancelling edit");
                    plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        new RewardsCategoryGui(plugin, player, editor.isPremium(),
                                ((editor.getLevel() - 1) / 45) + 1).open();
                    }, 1L);
                    break;
                case 49:
                    plugin.getLogger().info("Saving rewards for level " + editor.getLevel());
                    plugin.getRewardEditorManager().saveRewards(player, editor.getLevel(),
                            editor.isPremium(), editor.getCurrentRewards());
                    plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        new RewardsCategoryGui(plugin, player, editor.isPremium(),
                                ((editor.getLevel() - 1) / 45) + 1).open();
                    }, 1L);
                    break;
                case 50:
                    plugin.getLogger().info("Clearing all rewards");
                    editor.clearRewards();
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        editor.open();
                    }, 1L);
                    break;
            }
        }
    }
}