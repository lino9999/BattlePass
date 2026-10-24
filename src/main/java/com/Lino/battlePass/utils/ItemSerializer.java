package com.Lino.battlePass.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class ItemSerializer {

    public static void saveItemToConfig(ConfigurationSection section, ItemStack item) {
        if (!isUsableItem(item)) {
            return;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(item);
            }
            String base64 = encodeItemData(outputStream.toByteArray());
            section.set("serialized-item", base64);

            section.set("material", item.getType().name());
            section.set("amount", item.getAmount());

            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();

                if (meta.hasDisplayName()) {
                    section.set("display-name", meta.getDisplayName());
                }

                if (meta.hasLore()) {
                    section.set("lore", meta.getLore());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ItemStack loadItemFromConfig(ConfigurationSection section) {
        return loadItemFromConfig(section, false);
    }

    public static ItemStack loadItemFromConfigStrict(ConfigurationSection section) {
        return loadItemFromConfig(section, true);
    }

    private static ItemStack loadItemFromConfig(ConfigurationSection section, boolean strict) {
        if (section == null) {
            return null;
        }

        if (section.contains("serialized-item")) {
            try {
                String data = section.getString("serialized-item");
                try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(
                        new ByteArrayInputStream(decodeItemData(data)))) {
                    Object decoded = dataInput.readObject();
                    if (!(decoded instanceof ItemStack item) || !isUsableItem(item)) {
                        throw new IllegalStateException("Serialized reward is not a usable item");
                    }
                    return item;
                }
            } catch (Exception e) {
                if (strict) throw new IllegalStateException("Invalid serialized reward item", e);
                e.printStackTrace();
            }
        }

        String materialName = section.getString("material");
        if (materialName == null) {
            return null;
        }

        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!material.isItem() || material.isAir()) {
            if (strict) throw new IllegalStateException("Reward material is not a usable item: " + materialName);
            return null;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (section.contains("display-name")) {
                meta.setDisplayName(section.getString("display-name"));
            }

            if (section.contains("lore")) {
                meta.setLore(section.getStringList("lore"));
            }

            if (section.contains("enchantments")) {
                ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
                if (enchantSection != null) {
                    for (String key : enchantSection.getKeys(false)) {
                        Enchantment enchant = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
                        if (enchant != null) {
                            meta.addEnchant(enchant, enchantSection.getInt(key), true);
                        }
                    }
                }
            }

            if (section.contains("item-flags")) {
                List<String> flags = section.getStringList("item-flags");
                for (String flag : flags) {
                    try {
                        meta.addItemFlags(ItemFlag.valueOf(flag));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (section.contains("custom-model-data")) {
                meta.setCustomModelData(section.getInt("custom-model-data"));
            }

            if (section.getBoolean("unbreakable", false)) {
                meta.setUnbreakable(true);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static boolean isUsableItem(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType() != null
                && item.getType().isItem() && !item.getType().isAir();
    }

    private static String encodeItemData(byte[] data) {
        return Base64.getMimeEncoder(76, new byte[] {'\n'}).encodeToString(data);
    }

    private static byte[] decodeItemData(String data) {
        // Retain legacy line-wrapped payloads without depending on SnakeYAML's removed internal codec.
        String compact = data.replace("\r", "").replace("\n", "").replace("\t", "").replace(" ", "");
        if (compact.length() % 4 != 0) {
            throw new IllegalArgumentException("Invalid Base64 reward item length");
        }
        return Base64.getDecoder().decode(compact);
    }
}
