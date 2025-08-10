package com.Lino.battlePass.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemSerializer {

    public static void saveItemToConfig(ConfigurationSection section, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            String base64 = Base64Coder.encodeLines(outputStream.toByteArray());
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
        if (section == null) {
            return null;
        }

        if (section.contains("serialized-item")) {
            try {
                String data = section.getString("serialized-item");
                ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                ItemStack item = (ItemStack) dataInput.readObject();
                dataInput.close();
                return item;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String materialName = section.getString("material");
        if (materialName == null) {
            return null;
        }

        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        int amount = section.getInt("amount", 1);
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
                        Enchantment enchant = Enchantment.getByName(key.toUpperCase());
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
}