package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MissionDetailsGui {

    private final BattlePass plugin;
    private final Player player;
    private final String missionKey;

    public MissionDetailsGui(BattlePass plugin, Player player, String missionKey) {
        this.plugin = plugin;
        this.player = player;
        this.missionKey = missionKey;
    }

    public void open() {
        Inventory gui = Bukkit.createInventory(null, 36, GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Редактирование: " + missionKey + "</gradient>"));
        ConfigurationSection section = plugin.getConfigManager().getMissionsConfig().getConfigurationSection("mission-pools." + missionKey);

        if (section == null) {
            player.sendMessage("§cМиссия не найдена!");
            player.closeInventory();
            return;
        }

        String type = section.getString("type");

        gui.setItem(10, createEditItem(Material.NAME_TAG, "&eНазвание", section.getString("display-name")));
        gui.setItem(11, createEditItem(Material.DIAMOND_SWORD, "&eТип", type));

        if (plugin.getMissionEditorManager().isTargetRequired(type)) {
            gui.setItem(12, createEditItem(Material.TARGET, "&eЦель", section.getString("target")));
        } else {
            gui.setItem(12, createNonEditItem(Material.BARRIER, "&7Цель", "&7Не требуется для " + type));
        }

        gui.setItem(14, createEditItem(Material.IRON_INGOT, "&eМин. требование", String.valueOf(section.getInt("min-required"))));
        gui.setItem(15, createEditItem(Material.GOLD_INGOT, "&eМакс. требование", String.valueOf(section.getInt("max-required"))));

        gui.setItem(23, createEditItem(Material.EXPERIENCE_BOTTLE, "&eМин. XP", String.valueOf(section.getInt("min-xp"))));
        gui.setItem(24, createEditItem(Material.EXPERIENCE_BOTTLE, "&eМакс. XP", String.valueOf(section.getInt("max-xp"))));

        gui.setItem(22, createEditItem(Material.ANVIL, "&eВес", String.valueOf(section.getInt("weight"))));

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse("&cНазад к списку"));
        back.setItemMeta(meta);
        gui.setItem(31, back);

        player.openInventory(gui);
    }

    private ItemStack createEditItem(Material mat, String title, String value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse(title));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse("&7Текущее: &f" + value));
        lore.add("");
        lore.add(GradientColorParser.parse("&eНажмите, чтобы изменить"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNonEditItem(Material mat, String title, String reason) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse(title));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse(reason));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
