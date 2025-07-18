package com.Lino.battlePass.models;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class ShopItem {
    public final int slot;
    public final Material material;
    public final String displayName;
    public final List<String> lore;
    public final int price;
    public final List<String> commands;
    public final List<ItemStack> items;

    public ShopItem(int slot, Material material, String displayName, List<String> lore,
                    int price, List<String> commands, List<ItemStack> items) {
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.price = price;
        this.commands = commands;
        this.items = items;
    }
}