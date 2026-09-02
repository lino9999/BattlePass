package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.BaseHolder;
import com.Lino.battlePass.managers.MissionEditorManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        if (plugin.getMissionEditorManager().isEditing(event.getPlayer().getUniqueId())) {
            MissionEditorManager.EditorState state = plugin.getMissionEditorManager().getEditingState(event.getPlayer().getUniqueId());
            if (state != null && state.type != MissionEditorManager.EditType.TYPE) {
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.getMissionEditorManager().handleChatInput(event.getPlayer(), event.getMessage()));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!BaseHolder.isBattlePassInventory(event.getView().getTopInventory())) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (title == null) return;

        if (title.startsWith("Mission Editor")) {
            if (!title.contains("Page ")) return;
            event.setCancelled(true);
            handleEditorClick(player, event, title);
        } else if (title.startsWith("Select Mission Type")) {
            if (event.getView().getTopInventory().getSize() != 54) return;
            ItemStack back = event.getView().getTopInventory().getItem(53);
            if (back == null || back.getType() != Material.BARRIER) return;

            event.setCancelled(true);
            handleTypeSelectionClick(player, event);
        } else if (title.startsWith("Editing: ")) {
            String missionKey = title.replace("Editing: ", "").trim();
            if (!plugin.getConfigManager().getMissionsConfig().contains("mission-pools." + missionKey)) return;

            event.setCancelled(true);
            handleDetailsClick(player, event, missionKey);
        }
    }

    private void handleEditorClick(Player player, InventoryClickEvent event, String title) {
        try {
            int page = Integer.parseInt(title.split("Page ")[1]);
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
                ItemMeta meta = clicked.getItemMeta();
                String key = ChatColor.stripColor(meta.getDisplayName());

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