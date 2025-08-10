package com.Lino.battlePass.models;

import org.bukkit.inventory.ItemStack;

public class EditableReward {

    private ItemStack itemStack;
    private String command;
    private String displayName;
    private boolean isCommand;

    public EditableReward(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.isCommand = false;
    }

    public EditableReward(String command, String displayName) {
        this.command = command;
        this.displayName = displayName;
        this.isCommand = true;
    }

    public boolean isCommand() {
        return isCommand;
    }

    public ItemStack getItemStack() {
        return itemStack != null ? itemStack.clone() : null;
    }

    public String getCommand() {
        return command;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack != null ? itemStack.clone() : null;
        this.isCommand = false;
        this.command = null;
        this.displayName = null;
    }

    public void setCommand(String command, String displayName) {
        this.command = command;
        this.displayName = displayName;
        this.isCommand = true;
        this.itemStack = null;
    }
}