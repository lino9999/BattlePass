package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionProgressListener implements Listener {

    private final BattlePass plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Double> distanceBuffer = new ConcurrentHashMap<>();
    private final Set<Material> oreTypes = EnumSet.noneOf(Material.class);
    private final Map<UUID, Map<Location, Long>> recentlyPlacedBlocks = new ConcurrentHashMap<>();

    public MissionProgressListener(BattlePass plugin) {
        this.plugin = plugin;
        initializeOreTypes();
    }

    private void initializeOreTypes() {
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("GILDED_BLACKSTONE")) {
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
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location last = lastLocations.get(uuid);
        if (last == null) {
            lastLocations.put(uuid, to);
            return;
        }

        if (last.getWorld() == null || to.getWorld() == null
                || !last.getWorld().getName().equals(to.getWorld().getName())) {
            lastLocations.put(uuid, to);
            distanceBuffer.remove(uuid);
            return;
        }

        double distance;
        try {
            distance = last.distance(to);
        } catch (IllegalArgumentException e) {
            lastLocations.put(uuid, to);
            distanceBuffer.remove(uuid);
            return;
        }

        if (distance > 0 && distance < 100) {

            String mode;
            if (player.isFlying() || player.isGliding()) {
                mode = "FLY";
            } else if (player.isSwimming() || player.getLocation().getBlock().isLiquid()) {
                mode = "SWIM";
            } else if (player.isSneaking()) {
                mode = "SNEAK";
            } else {
                mode = "WALK";
            }

            double buf = distanceBuffer.getOrDefault(uuid, 0.0);
            buf += distance;
            int whole = (int) buf;
            if (whole > 0) {
                plugin.getMissionManager().progressMission(player, "WALK_DISTANCE", mode, whole);
                buf -= whole;
            }
            distanceBuffer.put(uuid, buf);
        }

        lastLocations.put(uuid, to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) {
            plugin.getMissionManager().progressMission(player, "DEATH", "UNKNOWN", 1);
            return;
        }

        for (String type : enumerateDamageTypes(last)) {
            plugin.getMissionManager().progressMission(player, "DEATH", type, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int amount = (int) event.getFinalDamage();

        for (String type : enumerateDamageTypes(event)) {
            plugin.getMissionManager().progressMission(player, "DAMAGE_TAKEN", type, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity trueDamager = getTrueDamager(event.getDamager());
        if (trueDamager instanceof Player player) {
            int amount = (int) event.getFinalDamage();
            for (String type : enumerateDamageTypes(event)) {
                plugin.getMissionManager().progressMission(player, "DAMAGE_DEALT", type, amount);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        if (event.getInventory().getHolder() instanceof Villager villager) {
            String profession = getProfessionName(villager);
            if (profession.equals("NONE")) profession = "VILLAGER";

            plugin.getMissionManager().progressMission(player, "TRADE_VILLAGER", profession, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item.getType() != Material.AIR) {
            String itemType = item.getType().name();
            plugin.getMissionManager().progressMission(player, "ENCHANT_ITEM", itemType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            Player player = event.getPlayer();
            if (event.getCaught() instanceof org.bukkit.entity.Item item) {
                String itemType = item.getItemStack().getType().name();
                plugin.getMissionManager().progressMission(player, "FISH_ITEM", itemType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Location blockLoc = event.getBlock().getLocation();
        UUID uuid = player.getUniqueId();

        Map<Location, Long> playerPlacedBlocks = recentlyPlacedBlocks.computeIfAbsent(uuid, k -> new HashMap<>());

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

    public String getProfessionName(Villager villager) {
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

    private List<String> enumerateDamageTypes(EntityDamageEvent event) {
        List<String> result = new ArrayList<>();

        if (event instanceof EntityDamageByEntityEvent edbe) {
            Entity rawDamager = edbe.getDamager();

            if (rawDamager instanceof Projectile projectile) {
                result.add(safeName(projectile.getType().name()));

                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Entity shooterEntity) {
                    if (shooterEntity instanceof Player) {
                        result.add("PLAYER");
                    } else if (shooterEntity instanceof LivingEntity livingShooter) {
                        String mobBase = safeName(livingShooter.getType().name());
                        result.add(mobBase);
                    } else {
                        result.add(safeName(shooterEntity.getType().name()));
                    }
                }
            } else {
                if (rawDamager instanceof Player) {
                    result.add("PLAYER");
                } else if (rawDamager instanceof LivingEntity living) {
                    result.add(safeName(living.getType().name()));
                } else {
                    result.add(safeName(rawDamager.getType().name()));
                }
            }
        }

        result.add(safeName(getCauseName(event.getCause())));

        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    private Entity getTrueDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource ps = projectile.getShooter();
            if (ps instanceof Entity shooterEntity) return shooterEntity;
        }
        return damager;
    }

    private String getCauseName(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case FIRE, FIRE_TICK -> {
                return "FIRE";
            }
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> {
                return "EXPLOSION";
            }
            default -> {
                return cause.name();
            }
        }
    }

    private String safeName(String s) {
        return s == null ? "UNKNOWN" : s.replaceAll("[^A-Z0-9_]", "_").toUpperCase();
    }

    public void initializePlayerLocation(UUID uuid, Location location) {
        lastLocations.put(uuid, location);
        recentlyPlacedBlocks.put(uuid, new HashMap<>());
    }

    public void cleanupPlayer(UUID uuid) {
        lastLocations.remove(uuid);
        distanceBuffer.remove(uuid);
        recentlyPlacedBlocks.remove(uuid);
    }
}