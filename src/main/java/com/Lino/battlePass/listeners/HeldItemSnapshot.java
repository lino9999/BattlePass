package com.Lino.battlePass.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Binds a synchronous redemption to its original hand, slot, metadata and quantity. */
final class HeldItemSnapshot {
    private final EquipmentSlot hand;
    private final int slot;
    private final int amount;
    private final ItemStack item;

    HeldItemSnapshot(Player player, EquipmentSlot hand, ItemStack item) {
        this.hand = hand;
        this.slot = hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : -1;
        this.amount = item.getAmount();
        this.item = item.clone();
    }

    boolean stillHeld(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return false;
        if (hand == EquipmentSlot.HAND && inventory.getHeldItemSlot() != slot) return false;
        if (hand == EquipmentSlot.OFF_HAND && inventory.getItemInMainHand().getType() != Material.AIR) return false;
        ItemStack current = hand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        return current != null && amount > 0 && current.getAmount() == amount && current.isSimilar(item);
    }
}
