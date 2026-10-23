package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Location;
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
import org.bukkit.event.player.*;

import java.util.*;

public class MissionProgressListener implements Listener {

    private final MovementMissionTracker movement;
    private final BlockMissionTracker blocks;
    private final CombatMissionTracker combat;
    private final InteractionMissionTracker interactions;

    public MissionProgressListener(BattlePass plugin) {
        movement = new MovementMissionTracker(plugin);
        blocks = new BlockMissionTracker(plugin);
        combat = new CombatMissionTracker(plugin);
        interactions = new InteractionMissionTracker(plugin, this::getProfessionName);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        movement.onPlayerChangedWorld(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        movement.onPlayerTeleport(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        movement.onPlayerMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        combat.onPlayerDeath(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        combat.onEntityDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        combat.onEntityDamageByEntity(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        interactions.onEntityBreed(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        interactions.onEntityTame(event);
    }

    /** Compatibility entry point; completed trades are counted by statistic increments. */
    @Deprecated
    public void onVillagerTrade(InventoryClickEvent event) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        interactions.onEnchantItem(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        interactions.onPlayerFish(event);
    }

    /** Compatibility entry point; completed crafts are counted by statistic increments. */
    @Deprecated
    public void onCraftItem(CraftItemEvent event) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        interactions.onStatisticIncrement(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        blocks.onBlockPlace(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        combat.onEntityDeath(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        blocks.onBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        interactions.onPlayerConsume(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        interactions.onFurnaceExtract(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        interactions.onPlayerExpChange(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        interactions.onPlayerShearEntity(event);
    }

    public String getProfessionName(Villager villager) {
        return interactions.getProfessionName(villager);
    }

    public void initializePlayerLocation(UUID uuid, Location location) {
        movement.initializePlayerLocation(uuid, location);
        blocks.initializePlayer(uuid);
    }

    public void cleanupPlayer(UUID uuid) {
        movement.cleanupPlayer(uuid);
        blocks.cleanupPlayer(uuid);
    }
}
