package com.Lino.battlePass.models;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Reward {
    public final int level;
    public final Material material;
    public final int amount;
    public final boolean isFree;
    public final String command;
    public final String displayName;
    public final String configurationPath;
    private final ItemStack itemSnapshot;

    public Reward(int level, Material material, String displayName, int amount, boolean isFree) {
        this(level, material, displayName, amount, isFree, null);
    }

    public Reward(int level, Material material, String displayName, int amount, boolean isFree, String configurationPath) {
        this.level = level;
        this.material = material;
        this.amount = amount;
        this.isFree = isFree;
        this.command = null;
        this.displayName = displayName;
        this.configurationPath = configurationPath;
        this.itemSnapshot = null;
    }

    public Reward(int level, ItemStack item, String displayName, boolean isFree, String configurationPath) {
        this.level = level;
        this.itemSnapshot = item.clone();
        this.material = item.getType();
        this.amount = item.getAmount();
        this.isFree = isFree;
        this.command = null;
        this.displayName = displayName;
        this.configurationPath = configurationPath;
    }

    /** Loaded definitions own their item; delivery must never mutate the shared snapshot. */
    public ItemStack getItemStack() {
        return itemSnapshot == null ? null : itemSnapshot.clone();
    }

    public Reward(int level, String command, String displayName, boolean isFree) {
        this.level = level;
        this.material = Material.COMMAND_BLOCK;
        this.amount = 1;
        this.isFree = isFree;
        this.command = command;
        this.displayName = displayName;
        this.configurationPath = null;
        this.itemSnapshot = null;
    }
}
