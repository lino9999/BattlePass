package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MissionsGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;

    public MissionsGui(BattlePass plugin, Player player) {
        super(plugin, plugin.getMessageManager().getMessage("gui.missions"), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
    }

    public void open() {
        Inventory gui = createInventory();

        setupTimerItem(gui);
        setupMissions(gui);
        gui.setItem(49, createBackButton());

        player.openInventory(gui);
    }

    private void setupTimerItem(Inventory gui) {
        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta meta = timer.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.mission-timer.name"));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.mission-timer.lore")) {
            String processedLine = line.replace("%reset_time%", plugin.getMissionManager().getTimeUntilReset());
            lore.add(GradientColorParser.parse(processedLine));
        }

        meta.setLore(lore);
        timer.setItemMeta(meta);
        gui.setItem(4, timer);
    }

    private void setupMissions(Inventory gui) {
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        List<Mission> currentMissions = plugin.getMissionManager().getDailyMissions();

        for (int i = 0; i < currentMissions.size() && i < slots.length; i++) {
            Mission mission = currentMissions.get(i);
            String key = mission.name.toLowerCase().replace(" ", "_");
            int progress = playerData.missionProgress.getOrDefault(key, 0);
            boolean completed = progress >= mission.required;

            ItemStack missionItem = new ItemStack(completed ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = missionItem.getItemMeta();

            String itemName = completed ? "items.mission-completed.name" : "items.mission-in-progress.name";
            String itemLore = completed ? "items.mission-completed.lore" : "items.mission-in-progress.lore";

            meta.setDisplayName(plugin.getMessageManager().getMessage(itemName, "%mission%", mission.name));

            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(itemLore)) {
                String processedLine = line
                        .replace("%progress%", String.valueOf(progress))
                        .replace("%required%", String.valueOf(mission.required))
                        .replace("%reward_xp%", String.valueOf(mission.xpReward))
                        .replace("%reset_time%", plugin.getMissionManager().getTimeUntilReset());
                lore.add(GradientColorParser.parse(processedLine));
            }

            meta.setLore(lore);
            missionItem.setItemMeta(meta);
            gui.setItem(slots[i], missionItem);
        }
    }
}