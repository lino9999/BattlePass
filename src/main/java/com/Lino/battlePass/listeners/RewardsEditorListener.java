package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.BaseHolder;
import com.Lino.battlePass.gui.LevelRewardEditGui;
import com.Lino.battlePass.gui.RewardsCategoryGui;
import com.Lino.battlePass.gui.RewardsEditorGui;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.Pagination;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;

public class RewardsEditorListener implements Listener {

    private final BattlePass plugin;

    public RewardsEditorListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        BaseHolder.MenuContext context = BaseHolder.contextOf(event.getView().getTopInventory());
        if (!isRewardEditor(context)) return;
        if (!player.hasPermission("battlepass.admin")) {
            event.setCancelled(true);
            return;
        }
        if (context.type() == BaseHolder.MenuType.REWARD_LEVEL_EDITOR) {
            if (context.editor() != null && context.editor() == plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId())) {
                handleLevelEditClick(player, event);
            } else event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (context.type() == BaseHolder.MenuType.REWARDS_EDITOR) handleRewardsEditorClick(player, slot);
        else handleRewardsCategoryClick(player, slot, context.premium(), context.page(), event.getCurrentItem());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;

        if (!(event.getWhoClicked() instanceof Player)) return;

        if (isRewardEditor(BaseHolder.contextOf(event.getView().getTopInventory()))) {
            boolean topInventory = false;
            for (int slot : event.getRawSlots()) {
                if (slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
                    topInventory = true;
                    break;
                }
            }
            if (topInventory) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        BaseHolder.MenuContext context = BaseHolder.contextOf(event.getView().getTopInventory());
        if (context == null || context.type() != BaseHolder.MenuType.REWARD_LEVEL_EDITOR) return;
        if (context.editor() != plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId())) return;
        if (!plugin.getRewardEditorManager().hasCommandInput(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                InventoryView view = player.getOpenInventory();
                BaseHolder.MenuContext current = BaseHolder.contextOf(view == null ? null : view.getTopInventory());
                if ((current == null || current.editor() != context.editor())
                        && plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId()) == context.editor()) {
                    plugin.getRewardEditorManager().removeActiveEditor(player.getUniqueId());
                }
            }, 5L);
        }
    }

    private boolean isRewardEditor(BaseHolder.MenuContext context) {
        return context != null && switch (context.type()) {
            case REWARDS_EDITOR, REWARDS_CATEGORY, REWARD_LEVEL_EDITOR -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRewardEditorManager().hasCommandInput(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && (player.hasPermission("battlepass.admin") || "cancel".equalsIgnoreCase(message))) {
                    plugin.getRewardEditorManager().handleCommandInput(player, message);
                }
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
                plugin.getRewardEditorManager().clearSeasonEditingContext(player.getUniqueId());
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getGuiManager().openBattlePassGUI(player, page);
                }, 1L);
                break;
        }
    }

    private void handleRewardsCategoryClick(Player player, int slot, boolean isPremium, int currentPage, ItemStack clicked) {
        if (clicked == null) return;
        if (slot < 45) {
            int level = Pagination.levelAt(currentPage, 45, slot);
            if (level == 0) return;

            player.closeInventory();
            final int finalLevel = level;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getRewardEditorManager().openLevelEditor(player, finalLevel, finalIsPremium);
            }, 1L);

        } else if (slot == 45 && clicked.getType() == org.bukkit.Material.ARROW && currentPage > 1) {
            player.closeInventory();
            final int prevPage = currentPage - 1;
            final boolean finalIsPremium = isPremium;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                new RewardsCategoryGui(plugin, player, finalIsPremium, prevPage).open();
            }, 1L);
        } else if (slot == 53 && clicked.getType() == org.bukkit.Material.ARROW) {
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
            plugin.getRewardEditorManager().clearSeasonEditingContext(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getGuiManager().openBattlePassGUI(player, 1);
            }, 20L);
        }
    }

    private void handleLevelEditClick(Player player, InventoryClickEvent event) {
        LevelRewardEditGui editor = plugin.getRewardEditorManager().getActiveEditor(player.getUniqueId());

        if (editor == null) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int inventorySize = event.getInventory().getSize();

        if (rawSlot >= inventorySize) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                if (item != null && !item.getType().isAir()) {
                    editor.addReward(new EditableReward(item.clone()));
                    refreshEditor(player, editor);
                }
            }
            return;
        }

        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();

        if (clickedSlot >= 0 && clickedSlot < 45) {
            ItemStack cursor = event.getCursor();
            ItemStack clicked = event.getCurrentItem();

            if (cursor != null && !cursor.getType().isAir()) {
                editor.addReward(new EditableReward(cursor.clone()));
                refreshEditor(player, editor);
            } else if (clicked != null && !clicked.getType().isAir()) {
                editor.removeReward(clickedSlot);
                refreshEditor(player, editor);
            }
        } else if (clickedSlot >= 46 && clickedSlot < 54) {
            handleControlButtons(player, editor, clickedSlot);
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            editor.open();
        });
    }
}
