package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CustomItemManager {

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

        meta.setDisplayName(GradientColorParser.parse("<gradient:#FF00FF:#00FFFF>★ Premium Battle Pass Voucher ★</gradient>"));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#FFD700:#FFA500>Mystical Voucher</gradient>"));
        lore.add(GradientColorParser.parse("&7A mysterious artifact pulsing with"));
        lore.add(GradientColorParser.parse("&7ancient power, granting access to"));
        lore.add(GradientColorParser.parse("&7the premium rewards of legends"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▼ Effects ▼</gradient>"));
        lore.add(GradientColorParser.parse("&7• Unlocks <gradient:#FFD93D:#FF6B6B>Premium Battle Pass</gradient>"));
        lore.add(GradientColorParser.parse("&7• Access to exclusive rewards"));
        lore.add(GradientColorParser.parse("&7• Permanent upgrade for this season"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>➤ RIGHT CLICK TO ACTIVATE</gradient>"));
        lore.add("");
        lore.add(GradientColorParser.parse("&8&oThe star pulses with ethereal energy..."));

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(premiumItemKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBattleCoinsItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FFA500>✦ Battle Coin Token ✦</gradient>"));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#FFD700:#FFFF00>Gleaming Token</gradient>"));
        lore.add(GradientColorParser.parse("&7A shimmering golden token that"));
        lore.add(GradientColorParser.parse("&7sparkles with magical radiance,"));
        lore.add(GradientColorParser.parse("&7containing concentrated battle essence"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▼ Value ▼</gradient>"));
        lore.add(GradientColorParser.parse("&7• Worth <gradient:#FFD93D:#FF6B6B>1 Battle Coin</gradient>"));
        lore.add(GradientColorParser.parse("&7• Stackable currency"));
        lore.add(GradientColorParser.parse("&7• Use in the Battle Shop"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>➤ RIGHT CLICK TO REDEEM</gradient>"));
        lore.add("");
        lore.add(GradientColorParser.parse("&8&oThe token glimmers with golden light..."));

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(coinsItemKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createLevelBoostItem(int amount) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(GradientColorParser.parse("<gradient:#00FF00:#00FFFF>✧ Experience Boost Elixir ✧</gradient>"));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>Experience Elixir</gradient>"));
        lore.add(GradientColorParser.parse("&7A powerful elixir infused with"));
        lore.add(GradientColorParser.parse("&7concentrated experience essence,"));
        lore.add(GradientColorParser.parse("&7boosting your battle pass progress"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▼ Power ▼</gradient>"));
        lore.add(GradientColorParser.parse("&7• Grants <gradient:#FFD93D:#FF6B6B>+100 Battle Pass XP</gradient>"));
        lore.add(GradientColorParser.parse("&7• Instant experience boost"));
        lore.add(GradientColorParser.parse("&7• Stackable consumable"));
        lore.add("");
        lore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>➤ RIGHT CLICK TO CONSUME</gradient>"));
        lore.add("");
        lore.add(GradientColorParser.parse("&8&oThe elixir bubbles with raw experience..."));

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(levelItemKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isPremiumPassItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(premiumItemKey, PersistentDataType.BOOLEAN);
    }

    public boolean isBattleCoinsItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(coinsItemKey, PersistentDataType.BOOLEAN);
    }

    public boolean isLevelBoostItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(levelItemKey, PersistentDataType.BOOLEAN);
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
}