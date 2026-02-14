package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CustomItemManager {

    private static final byte ITEM_MARKER = (byte) 1;

    private final BattlePass plugin;
    private final NamespacedKey premiumItemKey;
    private final NamespacedKey coinsItemKey;
    private final NamespacedKey levelItemKey;

    public CustomItemManager(BattlePass plugin) {
        this.plugin = plugin;
        this.premiumItemKey = new NamespacedKey(plugin, "premium_pass_item");
        this.coinsItemKey = new NamespacedKey(plugin, "battle_coins_item");
        this.levelItemKey = new NamespacedKey(plugin, "level_boost_item");
    }

    public ItemStack createPremiumPassItem(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("custom-items.premium-pass.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("custom-items.premium-pass.lore"));
        markAsSpecial(meta, premiumItemKey);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBattleCoinsItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("custom-items.battle-coin.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("custom-items.battle-coin.lore"));
        markAsSpecial(meta, coinsItemKey);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createLevelBoostItem(int amount) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("custom-items.level-boost.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("custom-items.level-boost.lore"));
        markAsSpecial(meta, levelItemKey);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isPremiumPassItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(premiumItemKey, PersistentDataType.BYTE);
    }

    public boolean isBattleCoinsItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(coinsItemKey, PersistentDataType.BYTE);
    }

    public boolean isLevelBoostItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(levelItemKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getPremiumItemKey() {
        return premiumItemKey;
    }

    public NamespacedKey getCoinsItemKey() {
        return coinsItemKey;
    }

    public NamespacedKey getLevelItemKey() {
        return levelItemKey;
    }

    private void markAsSpecial(ItemMeta meta, NamespacedKey key) {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, ITEM_MARKER);
    }
}
