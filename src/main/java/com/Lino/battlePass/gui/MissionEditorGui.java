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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class MissionEditorGui {

    private final BattlePass plugin;
    private final Player player;
    private final int page;

    public MissionEditorGui(BattlePass plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
    }

    public void open() {
        Inventory gui = Bukkit.createInventory(null, 54, GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>Mission Editor - Page " + page + "</gradient>"));

        ConfigurationSection pools = plugin.getConfigManager().getMissionsConfig().getConfigurationSection("mission-pools");
        List<String> keys = pools != null ? new ArrayList<>(pools.getKeys(false)) : new ArrayList<>();

        int slotsPerPage = 45;
        int start = (page - 1) * slotsPerPage;
        int end = Math.min(start + slotsPerPage, keys.size());

        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            gui.setItem(i - start, createMissionIcon(key, pools.getConfigurationSection(key)));
        }

        if (page > 1) {
            ItemStack prev = createButton(Material.ARROW, "&ePrevious Page");
            gui.setItem(45, prev);
        }

        if (end < keys.size()) {
            ItemStack next = createButton(Material.ARROW, "&eNext Page");
            gui.setItem(53, next);
        }

        gui.setItem(49, createButton(Material.EMERALD, "&a+ Create New Mission"));
        gui.setItem(48, createButton(Material.BARRIER, "&cBack to Menu"));

        player.openInventory(gui);
    }

    private ItemStack createMissionIcon(String key, ConfigurationSection section) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse("&e" + key));

        List<String> lore = new ArrayList<>();
        lore.add(GradientColorParser.parse("&7Type: &f" + section.getString("type")));
        lore.add(GradientColorParser.parse("&7Target: &f" + section.getString("target")));
        lore.add(GradientColorParser.parse("&7Display: &f" + section.getString("display-name")));
        lore.add("");
        lore.add(GradientColorParser.parse("&eLeft-Click to Edit"));
        lore.add(GradientColorParser.parse("&cRight-Click to Delete"));

        meta.setLore(lore);
        // Store key in persistent data
        meta.getPersistentDataContainer().set(plugin.getCustomItemManager().getPremiumItemKey(), PersistentDataType.STRING, key);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse(name));
        item.setItemMeta(meta);
        return item;
    }
}