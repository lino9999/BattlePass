package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/** Tracks item consumption, crafting, trading and animal interactions. */
final class InteractionMissionTracker {

    private final BattlePass plugin;
    private final Function<Villager, String> professionName;

    InteractionMissionTracker(BattlePass plugin, Function<Villager, String> professionName) {
        this.plugin = plugin;
        this.professionName = professionName;
    }

    void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    void onVillagerTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        if (event.getInventory().getHolder() instanceof Villager villager) {
            String profession = professionName.apply(villager);
            if (profession.equals("NONE")) profession = "VILLAGER";

            plugin.getMissionManager().progressMission(player, "TRADE_VILLAGER", profession, 1);
        }
    }

    void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item.getType() != Material.AIR) {
            String itemType = item.getType().name();
            plugin.getMissionManager().progressMission(player, "ENCHANT_ITEM", itemType, 1);
        }
    }

    void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            Player player = event.getPlayer();
            if (event.getCaught() instanceof org.bukkit.entity.Item item) {
                String itemType = item.getItemStack().getType().name();
                plugin.getMissionManager().progressMission(player, "FISH_ITEM", itemType, 1);
            }
        }
    }

    void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();

        if (result == null || result.getType() == Material.AIR) return;

        String itemType = result.getType().name();
        int crafted = result.getAmount();

        if (event.isShiftClick()) {
            ItemStack[] matrix = event.getInventory().getMatrix();
            int minIngredientCount = Integer.MAX_VALUE;

            for (ItemStack ingredient : matrix) {
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    int maxCraftable = ingredient.getAmount();
                    minIngredientCount = Math.min(minIngredientCount, maxCraftable);
                }
            }

            if (minIngredientCount != Integer.MAX_VALUE && minIngredientCount > 0) {
                crafted = result.getAmount() * minIngredientCount;
            }
        }

        plugin.getMissionManager().progressMission(player, "CRAFT_ITEM", itemType, crafted);
    }

    void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String itemType = event.getItem().getType().name();
        plugin.getMissionManager().progressMission(player, "EAT_ITEM", itemType, 1);
    }

    void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        String itemType = event.getItemType().name();
        int amount = event.getItemAmount();
        plugin.getMissionManager().progressMission(player, "SMELT_ITEM", itemType, amount);
    }

    void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) {
            plugin.getMissionManager().progressMission(event.getPlayer(), "GAIN_XP", "ANY", event.getAmount());
        }
    }

    void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getEntity().getType() == EntityType.SHEEP) {
            plugin.getMissionManager().progressMission(player, "SHEAR_SHEEP", "SHEEP", 1);
        }
    }

    String getProfessionName(Villager villager) {
        Villager.Profession profession = villager.getProfession();

        try {
            // Old method
            return profession.name();
        } catch (Exception e) {
            // New method ('name()' is deprecated since version 1.21 and marked for removal)
            // profession.key().value().replace("minecraft:", "").toUpperCase();
            try {
                Method keyMethod = profession.getClass().getMethod("key");
                Object namespacedKey = keyMethod.invoke(profession);
                Method valueMethod = namespacedKey.getClass().getMethod("value");
                String keyValue = (String) valueMethod.invoke(namespacedKey);
                return keyValue.replace("minecraft:", "").toUpperCase();
            } catch (Exception ex) {
                return "UNKNOWN";
            }
        }
    }
}
