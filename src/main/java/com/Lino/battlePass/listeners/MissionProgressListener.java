package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionProgressListener implements Listener {

    private final BattlePass plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Set<Material> oreTypes = EnumSet.noneOf(Material.class);
    private final Map<UUID, Map<Location, Long>> recentlyPlacedBlocks = new ConcurrentHashMap<>();

    public MissionProgressListener(BattlePass plugin) {
        this.plugin = plugin;
        initializeOreTypes();
    }

    private void initializeOreTypes() {
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("NETHER_QUARTZ_ORE") || name.equals("GILDED_BLACKSTONE")) {
                oreTypes.add(mat);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastLocations.put(uuid, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        if (from.getWorld() == null || to.getWorld() == null ||
                !from.getWorld().equals(to.getWorld()) ||
                from.distance(to) > 100) {
            lastLocations.put(uuid, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location last = lastLocations.get(uuid);
        Location toLoc = event.getTo();

        if (last != null) {
            if (last.getWorld() == null || toLoc.getWorld() == null ||
                    !last.getWorld().getName().equals(toLoc.getWorld().getName())) {
                lastLocations.put(uuid, toLoc);
                return;
            }

            try {
                double distance = last.distance(toLoc);
                if (distance >= 1 && distance < 100) {
                    plugin.getMissionManager().progressMission(player, "WALK_DISTANCE", "ANY", (int) distance);
                }
            } catch (IllegalArgumentException e) {
            }
            lastLocations.put(uuid, toLoc);
        } else {
            lastLocations.put(uuid, toLoc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getMissionManager().progressMission(event.getEntity(), "DEATH", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            plugin.getMissionManager().progressMission(player, "DAMAGE_DEALT", "ANY", (int) event.getDamage());
        }

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            plugin.getMissionManager().progressMission(player, "DAMAGE_TAKEN", "ANY", (int) event.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player) {
            Player player = (Player) event.getOwner();
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Player player = event.getPlayer();
            plugin.getMissionManager().progressMission(player, "TRADE_VILLAGER", "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        plugin.getMissionManager().progressMission(event.getEnchanter(), "ENCHANT_ITEM", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            Player player = event.getPlayer();
            if (event.getCaught() instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item item = (org.bukkit.entity.Item) event.getCaught();
                String itemType = item.getItemStack().getType().name();
                plugin.getMissionManager().progressMission(player, "FISH_ITEM", itemType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Location blockLoc = event.getBlock().getLocation();
        UUID uuid = player.getUniqueId();

        Map<Location, Long> playerPlacedBlocks = recentlyPlacedBlocks.get(uuid);
        if (playerPlacedBlocks == null) {
            playerPlacedBlocks = new HashMap<>();
            recentlyPlacedBlocks.put(uuid, playerPlacedBlocks);
        }

        Long lastPlaced = playerPlacedBlocks.get(blockLoc);
        long currentTime = System.currentTimeMillis();

        if (lastPlaced == null || currentTime - lastPlaced > 100) {
            plugin.getMissionManager().progressMission(player, "PLACE_BLOCK", blockType, 1);
            playerPlacedBlocks.put(blockLoc, currentTime);

            final Map<Location, Long> finalPlayerPlacedBlocks = playerPlacedBlocks;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                finalPlayerPlacedBlocks.remove(blockLoc);
            }, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();

            if (event.getEntity() instanceof Player) {
                plugin.getMissionManager().progressMission(killer, "KILL_PLAYER", "PLAYER", 1);
            } else {
                String entityType = event.getEntityType().name();
                plugin.getMissionManager().progressMission(killer, "KILL_MOB", entityType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Material mat = event.getBlock().getType();

        plugin.getMissionManager().progressMission(player, "BREAK_BLOCK", blockType, 1);

        if (event.isDropItems() && oreTypes.contains(mat)) {
            plugin.getMissionManager().progressMission(player, "MINE_BLOCK", blockType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String itemType = event.getItem().getType().name();
        plugin.getMissionManager().progressMission(player, "EAT_ITEM", itemType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        String itemType = event.getItemType().name();
        int amount = event.getItemAmount();
        plugin.getMissionManager().progressMission(player, "SMELT_ITEM", itemType, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) {
            plugin.getMissionManager().progressMission(event.getPlayer(), "GAIN_XP", "ANY", event.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getEntity().getType() == EntityType.SHEEP) {
            plugin.getMissionManager().progressMission(player, "SHEAR_SHEEP", "SHEEP", 1);
        }
    }

    public void initializePlayerLocation(UUID uuid, Location location) {
        lastLocations.put(uuid, location);
        recentlyPlacedBlocks.put(uuid, new HashMap<>());
    }

    public void cleanupPlayer(UUID uuid) {
        lastLocations.remove(uuid);
        recentlyPlacedBlocks.remove(uuid);
    }
}