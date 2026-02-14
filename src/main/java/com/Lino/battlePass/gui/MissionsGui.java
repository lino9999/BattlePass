package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MissionsGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;

    public MissionsGui(BattlePass plugin, Player player) {
        super(plugin, plugin.getMessageManager().getGuiMessage("gui.missions"), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
    }

    public void open() {
        Inventory gui = createInventory();

        setupTimerItem(gui);
        setupMissions(gui);
        gui.setItem(49, createBackButton());

        if (player.hasPermission("battlepass.admin")) {
            setupAdminResetButton(gui);
            setupMissionEditorButton(gui);
        }

        player.openInventory(gui);
    }

    private void setupTimerItem(Inventory gui) {
        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta meta = timer.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.mission-timer.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("items.mission-timer.lore",
                "%reset_time%", plugin.getMissionManager().getTimeUntilReset()));

        timer.setItemMeta(meta);
        gui.setItem(4, timer);
    }

    private void setupMissions(Inventory gui) {
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        List<Mission> currentMissions = plugin.getMissionManager().getDailyMissions();

        for (int i = 0; i < currentMissions.size() && i < slots.length; i++) {
            Mission mission = currentMissions.get(i);
            String key = mission.type + "_" + mission.target + "_" + mission.required + "_" + mission.name.hashCode();

            int progress = playerData.missionProgress.getOrDefault(key, 0);
            boolean completed = progress >= mission.required;

            ItemStack missionItem = new ItemStack(completed ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = missionItem.getItemMeta();

            String namePath = completed ? "items.mission-completed.name" : "items.mission-in-progress.name";
            String lorePath = completed ? "items.mission-completed.lore" : "items.mission-in-progress.lore";

            meta.setDisplayName(plugin.getMessageManager().getGuiMessage(namePath, "%mission%", mission.name));
            meta.setLore(plugin.getMessageManager().getGuiMessages(lorePath,
                    "%progress%", String.valueOf(progress),
                    "%required%", String.valueOf(mission.required),
                    "%reward_xp%", String.valueOf(mission.xpReward),
                    "%reset_time%", plugin.getMissionManager().getTimeUntilReset()));

            missionItem.setItemMeta(meta);
            gui.setItem(slots[i], missionItem);
        }
    }

    private void setupAdminResetButton(Inventory gui) {
        ItemStack resetButton = new ItemStack(Material.TNT);
        ItemMeta meta = resetButton.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.admin-missions-reset.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("items.admin-missions-reset.lore"));
        resetButton.setItemMeta(meta);
        gui.setItem(45, resetButton);
    }

    private void setupMissionEditorButton(Inventory gui) {
        ItemStack editButton = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = editButton.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.admin-missions-editor.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("items.admin-missions-editor.lore"));
        editButton.setItemMeta(meta);
        gui.setItem(53, editButton);
    }
}
