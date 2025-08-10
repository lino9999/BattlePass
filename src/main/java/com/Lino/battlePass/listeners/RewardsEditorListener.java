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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title != null && title.contains(EDIT_LEVEL_TITLE)) {
            if (!plugin.getRewardEditorManager().hasCommandInput(player.getUniqueId())) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    String newTitle = ChatColor.stripColor(player.getOpenInventory().getTitle());
                    if (newTitle == null || !newTitle.contains(EDIT_LEVEL_TITLE)) {
                        plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
                    }
                }, 5L);
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
        switch (slot) {
            case 11:
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RewardsCategoryGui(plugin, player, false, 1).open();
                }, 1L);
                break;
            case 15:
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RewardsCategoryGui(plugin, player, true, 1).open();
                }, 1L);
                break;
            case 22:
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getGuiManager().openBattlePassGUI(player, page);
                }, 1L);
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

        if (slot < 45) {
            int startLevel = (currentPage - 1) * 45 + 1;
            int level = startLevel + slot;

            if (level <= 54) {
                player.closeInventory();
                final int finalLevel = level;
                final boolean finalIsPremium = isPremium;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getRewardEditorManager().openLevelEditor(player, finalLevel, finalIsPremium);
                }, 1L);
            }
        } else if (slot == 45 && currentPage > 1) {
            player.closeInventory();
            final int prevPage = currentPage - 1;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsCategoryGui(plugin, player, finalIsPremium, prevPage).open();
            }, 1L);
        } else if (slot == 53 && ((currentPage - 1) * 45 + 45) < 54) {
            player.closeInventory();
            final int nextPage = currentPage + 1;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsCategoryGui(plugin, player, finalIsPremium, nextPage).open();
            }, 1L);
        } else if (slot == 48) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsEditorGui(plugin, player).open();
            }, 1L);
        } else if (slot == 49) {
            player.closeInventory();
            plugin.getRewardEditorManager().saveAllRewards(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getGuiManager().openBattlePassGUI(player, 1);
            }, 20L);
        }
    }

    private void handleLevelEditClick(Player player, InventoryClickEvent event) {
        LevelRewardEditGui editor = plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId());

        if (editor == null) {
            event.setCancelled(true);
            return;
        }

        int clickedSlot = event.getSlot();
        int rawSlot = event.getRawSlot();
        int inventorySize = event.getInventory().getSize();

        if (rawSlot >= inventorySize) {
            if (event.getAction() == InventoryAction.PICKUP_ALL ||
                    event.getAction() == InventoryAction.PICKUP_HALF ||
                    event.getAction() == InventoryAction.PICKUP_ONE ||
                    event.getAction() == InventoryAction.PICKUP_SOME) {
                return;
            }

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR) {
                    event.setCancelled(true);
                    editor.addReward(new EditableReward(item.clone()));
                    refreshEditor(player, editor);
                }
            }
            return;
        }

        event.setCancelled(true);

        if (rawSlot >= 0 && rawSlot < 36) {
            ItemStack currentItem = event.getInventory().getItem(rawSlot);
            ItemStack cursorItem = event.getCursor();

            if (currentItem != null && currentItem.getType() != Material.AIR) {
                editor.removeReward(rawSlot);
                refreshEditor(player, editor);
            }
            else if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                editor.addReward(new EditableReward(cursorItem.clone()));
                event.getView().setCursor(null);
                refreshEditor(player, editor);
            }
        }
        else if (rawSlot >= 36 && rawSlot < 54) {
            handleControlButtons(player, editor, rawSlot);
        }
    }

    private void handleControlButtons(Player player, LevelRewardEditGui editor, int slot) {
        switch (slot) {
            case 45:
                break;

            case 46:
                plugin.getRewardEditorManager().startCommandInput(player, editor.getLevel(), editor.isPremium());
                break;

            case 48:
                plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    new RewardsCategoryGui(plugin, player, editor.isPremium(),
                            ((editor.getLevel() - 1) / 45) + 1).open();
                }, 1L);
                break;

            case 49:
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
                editor.clearRewards();
                refreshEditor(player, editor);
                break;

            default:
                break;
        }
    }

    private void refreshEditor(Player player, LevelRewardEditGui editor) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            editor.open();
        }, 1L);
    }
}