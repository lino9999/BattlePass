package com.Lino.battlePass.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BaseHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static boolean isBattlePassInventory(Inventory inventory) {
        return inventory.getHolder() instanceof BaseHolder;
    }
}