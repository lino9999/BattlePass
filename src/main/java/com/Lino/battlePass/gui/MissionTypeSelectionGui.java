package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MissionTypeSelectionGui {

    private final BattlePass plugin;
    private final Player player;
    private static final Map<String, Material> MISSION_TYPES = new LinkedHashMap<>();

    static {
        MISSION_TYPES.put("KILL_MOB", Material.DIAMOND_SWORD);
        MISSION_TYPES.put("KILL_MYTHIC_MOB", Material.WITHER_SKELETON_SKULL);
        MISSION_TYPES.put("KILL_PLAYER", Material.IRON_SWORD);
        MISSION_TYPES.put("MINE_BLOCK", Material.DIAMOND_PICKAXE);
        MISSION_TYPES.put("BREAK_BLOCK", Material.STONE_PICKAXE);
        MISSION_TYPES.put("PLACE_BLOCK", Material.GRASS_BLOCK);
        MISSION_TYPES.put("CRAFT_ITEM", Material.CRAFTING_TABLE);
        MISSION_TYPES.put("SMELT_ITEM", Material.FURNACE);
        MISSION_TYPES.put("ENCHANT_ITEM", Material.ENCHANTED_BOOK);
        MISSION_TYPES.put("FISH_ITEM", Material.FISHING_ROD);
        MISSION_TYPES.put("BREED_ANIMAL", Material.WHEAT);
        MISSION_TYPES.put("TAME_ANIMAL", Material.BONE);
        MISSION_TYPES.put("SHEAR_SHEEP", Material.SHEARS);
        MISSION_TYPES.put("TRADE_VILLAGER", Material.EMERALD);
        MISSION_TYPES.put("EAT_ITEM", Material.COOKED_BEEF);
        MISSION_TYPES.put("WALK_DISTANCE", Material.LEATHER_BOOTS);
        MISSION_TYPES.put("PLAY_TIME", Material.CLOCK);
        MISSION_TYPES.put("GAIN_XP", Material.EXPERIENCE_BOTTLE);
        MISSION_TYPES.put("DAMAGE_DEALT", Material.REDSTONE);
        MISSION_TYPES.put("DAMAGE_TAKEN", Material.SHIELD);
        MISSION_TYPES.put("DEATH", Material.SKELETON_SKULL);
    }

    public MissionTypeSelectionGui(BattlePass plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Inventory gui = Bukkit.createInventory(null, 54, GradientColorParser.parse("<gradient:#FFD700:#FFA500>Select Mission Type</gradient>"));

        int slot = 0;
        for (Map.Entry<String, Material> entry : MISSION_TYPES.entrySet()) {
            if (slot >= 54) break;
            gui.setItem(slot, createTypeItem(entry.getKey(), entry.getValue()));
            slot++;
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse("&cBack to Editor"));
        back.setItemMeta(meta);
        gui.setItem(53, back);

        player.openInventory(gui);
    }

    private ItemStack createTypeItem(String type, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(GradientColorParser.parse("&e" + type));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(GradientColorParser.parse("&7Click to create a new"));
        lore.add(GradientColorParser.parse("&7mission of this type."));

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(plugin.getCustomItemManager().getPremiumItemKey(), PersistentDataType.STRING, type);

        item.setItemMeta(meta);
        return item;
    }
}