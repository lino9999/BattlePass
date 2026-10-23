package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks movement progress and per-player distance buffers. */
final class MovementMissionTracker {

    private final BattlePass plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Double> distanceBuffer = new ConcurrentHashMap<>();

    MovementMissionTracker(BattlePass plugin) {
        this.plugin = plugin;
    }

    void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastLocations.put(uuid, player.getLocation());
    }

    void onPlayerTeleport(PlayerTeleportEvent event) {
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

    void onPlayerMove(PlayerMoveEvent event) {
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

    void initializePlayerLocation(UUID uuid, Location location) {
        lastLocations.put(uuid, location);
    }

    void cleanupPlayer(UUID uuid) {
        lastLocations.remove(uuid);
        distanceBuffer.remove(uuid);
    }
}
