package com.Lino.battlePass.models;

import org.bukkit.Material;

public class Reward {
    public final int level;
    public final Material material;
    public final int amount;
    public final boolean isFree;
    public final String command;
    public final String displayName;

    public Reward(int level, Material material, int amount, boolean isFree) {
        this.level = level;
        this.material = material;
        this.amount = amount;
        this.isFree = isFree;
        this.command = null;
        this.displayName = amount + "x " + formatMaterial(material);
    }

    public Reward(int level, String command, String displayName, boolean isFree) {
        this.level = level;
        this.material = Material.COMMAND_BLOCK;
        this.amount = 1;
        this.isFree = isFree;
        this.command = command;
        this.displayName = displayName;
    }

    private static String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
}