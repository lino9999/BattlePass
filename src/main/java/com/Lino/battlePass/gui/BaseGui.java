package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseGui {

    protected final BattlePass plugin;
    protected final String title;
    protected final int size;

    public BaseGui(BattlePass plugin, String title, int size) {
        this.plugin = plugin;
        this.title = title;
        this.size = size;
    }

    public Inventory createInventory() {
        return Bukkit.createInventory(null, size, title);
    }

    protected ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (displayName != null) {
            meta.setDisplayName(displayName);
        }

        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.back-button.name"));

        List<String> lore = new ArrayList<>();
        lore.addAll(plugin.getMessageManager().getGuiMessages("items.back-button.lore"));
        meta.setLore(lore);
        back.setItemMeta(meta);

        return back;
    }

    protected String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
}
