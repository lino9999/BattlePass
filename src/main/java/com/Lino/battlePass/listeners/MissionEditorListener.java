package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.BaseHolder;
import com.Lino.battlePass.managers.MissionEditorManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class MissionEditorListener implements Listener {

    private final BattlePass plugin;

    public MissionEditorListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MissionEditorManager.EditorState state = plugin.getMissionEditorManager().getEditingState(player.getUniqueId());
        if (state != null && state.type != MissionEditorManager.EditType.TYPE) {
            event.setCancelled(true);
            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && (player.hasPermission("battlepass.admin") || "cancel".equalsIgnoreCase(message))) {
                    plugin.getMissionEditorManager().handleChatInput(player, message);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        BaseHolder.MenuContext context = BaseHolder.contextOf(event.getView().getTopInventory());
        if (!isMissionEditor(context)) return;
        event.setCancelled(true);
        if (!player.hasPermission("battlepass.admin") || !isTopSlot(event)) return;
        switch (context.type()) {
            case MISSION_EDITOR -> handleEditorClick(player, event, context);
            case MISSION_TYPE -> handleTypeSelectionClick(player, event);
            case MISSION_DETAILS -> {
                String key = context.missionKey();
                if (key != null && plugin.getConfigManager().getMissionsConfig().isConfigurationSection("mission-pools." + key)) {
                    handleDetailsClick(player, event, key);
                }
            }
            default -> { }
        }
    }

    private boolean isMissionEditor(BaseHolder.MenuContext context) {
        return context != null && switch (context.type()) {
            case MISSION_EDITOR, MISSION_TYPE, MISSION_DETAILS -> true;
            default -> false;
        };
    }

    private boolean isTopSlot(InventoryClickEvent event) {
        return event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isMissionEditor(BaseHolder.contextOf(event.getView().getTopInventory()))) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < size)) {
            event.setCancelled(true);
        }
    }

    private void handleEditorClick(Player player, InventoryClickEvent event, BaseHolder.MenuContext context) {
        try {
            int page = context.page();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) {
                if (event.getSlot() == 45) plugin.getMissionEditorManager().openMissionEditor(player, page - 1);
                if (event.getSlot() == 53) plugin.getMissionEditorManager().openMissionEditor(player, page + 1);
            } else if (clicked.getType() == Material.EMERALD) {
                plugin.getMissionEditorManager().openMissionTypeSelector(player);
            } else if (clicked.getType() == Material.BARRIER) {
                plugin.getGuiManager().openMissionsGUI(player);
            } else if (clicked.getType() == Material.PAPER) {
                String key = context.entries().get(event.getSlot());
                if (key == null) return;

                if (event.getClick() == ClickType.RIGHT) {
                    plugin.getMissionEditorManager().deleteMission(player, key);
                } else {
                    plugin.getMissionEditorManager().openMissionDetails(player, key);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleTypeSelectionClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.BARRIER) {
            if (plugin.getMissionEditorManager().isEditing(player.getUniqueId())) {
                MissionEditorManager.EditorState state = plugin.getMissionEditorManager().getEditingState(player.getUniqueId());
                plugin.getMissionEditorManager().openMissionDetails(player, state.missionKey);
            } else {
                plugin.getMissionEditorManager().openMissionEditor(player, 1);
            }
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(plugin.getCustomItemManager().getPremiumItemKey(), PersistentDataType.STRING)) {
            String type = meta.getPersistentDataContainer().get(plugin.getCustomItemManager().getPremiumItemKey(), PersistentDataType.STRING);

            if (plugin.getMissionEditorManager().isEditing(player.getUniqueId())) {
                plugin.getMissionEditorManager().updateMissionType(player, type);
            } else {
                plugin.getMissionEditorManager().createMissionFromType(player, type);
            }
        }
    }

    private void handleDetailsClick(Player player, InventoryClickEvent event, String key) {
        int slot = event.getSlot();
        MissionEditorManager.EditType type = null;
        boolean isTargetRequired = true;

        if (slot == 12) {
            ConfigurationSection section = plugin.getConfigManager().getMissionsConfig().getConfigurationSection("mission-pools." + key);
            if (section != null) {
                String missionType = section.getString("type", "UNKNOWN");
                isTargetRequired = plugin.getMissionEditorManager().isTargetRequired(missionType);
            }
        }

        switch (slot) {
            case 10: type = MissionEditorManager.EditType.DISPLAY_NAME; break;
            case 11: type = MissionEditorManager.EditType.TYPE; break;
            case 12: if (isTargetRequired) type = MissionEditorManager.EditType.TARGET; break;
            case 14: type = MissionEditorManager.EditType.MIN_REQ; break;
            case 15: type = MissionEditorManager.EditType.MAX_REQ; break;
            case 22: type = MissionEditorManager.EditType.WEIGHT; break;
            case 23: type = MissionEditorManager.EditType.MIN_XP; break;
            case 24: type = MissionEditorManager.EditType.MAX_XP; break;
            case 31:
                plugin.getMissionEditorManager().openMissionEditor(player, 1);
                return;
        }

        if (type != null) {
            plugin.getMissionEditorManager().startEditingValue(player, key, type);
        }
    }
}
