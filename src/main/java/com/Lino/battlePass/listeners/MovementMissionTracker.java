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
    private final Map<UUID, Map<String, Double>> distanceBuffer = new ConcurrentHashMap<>();

    MovementMissionTracker(BattlePass plugin) {
        this.plugin = plugin;
    }

    void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        initializePlayerLocation(uuid, player.getLocation());
    }

    void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location to = event.getTo();

        if (to == null) return;

        initializePlayerLocation(uuid, to);
    }

    void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        if (event instanceof PlayerTeleportEvent teleport) {
            onPlayerTeleport(teleport);
            return;
        }
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location last = lastLocations.get(uuid);
        if (last == null) {
            initializePlayerLocation(uuid, to);
            return;
        }

        if (last.getWorld() == null || to.getWorld() == null
                || !last.getWorld().equals(to.getWorld())
                || plugin.getConfigManager().isMissionWorldDisabled(to.getWorld().getName())) {
            initializePlayerLocation(uuid, to);
            return;
        }

        if (last.getX() == to.getX() && last.getY() == to.getY() && last.getZ() == to.getZ()) return;

        double distance;
        try {
            distance = last.distance(to);
        } catch (IllegalArgumentException e) {
            initializePlayerLocation(uuid, to);
            return;
        }

        if (Double.isFinite(distance) && distance > 0 && distance < 100) {

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

            Map<String, Double> buffers = distanceBuffer.computeIfAbsent(uuid, ignored -> new HashMap<>());
            double buf = buffers.getOrDefault(mode, 0.0);
            buf += distance;
            int whole = (int) buf;
            buffers.put(mode, buf - whole);
            lastLocations.put(uuid, to.clone());
            // Mission callbacks may teleport the player; commit this sample first.
            if (whole > 0) {
                plugin.getMissionManager().progressMission(player, "WALK_DISTANCE", mode, whole);
            }
        } else {
            initializePlayerLocation(uuid, to);
        }
    }

    void initializePlayerLocation(UUID uuid, Location location) {
        lastLocations.put(uuid, location.clone());
        distanceBuffer.remove(uuid);
    }

    void cleanupPlayer(UUID uuid) {
        lastLocations.remove(uuid);
        distanceBuffer.remove(uuid);
    }
}
