package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks block placement, mining and recent placements. */
final class BlockMissionTracker {

    private final BattlePass plugin;
    private final Set<Material> oreTypes = EnumSet.noneOf(Material.class);
    private final Map<UUID, Map<Location, Long>> recentlyPlacedBlocks = new ConcurrentHashMap<>();

    BlockMissionTracker(BattlePass plugin) {
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

    void onBlockPlace(BlockPlaceEvent event) {
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

    void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Material mat = event.getBlock().getType();

        plugin.getMissionManager().progressMission(player, "BREAK_BLOCK", blockType, 1);

        if (event.isDropItems() && oreTypes.contains(mat)) {
            plugin.getMissionManager().progressMission(player, "MINE_BLOCK", blockType, 1);
        }
    }

    void initializePlayer(UUID uuid) {
        recentlyPlacedBlocks.put(uuid, new HashMap<>());
    }

    void cleanupPlayer(UUID uuid) {
        recentlyPlacedBlocks.remove(uuid);
    }
}
