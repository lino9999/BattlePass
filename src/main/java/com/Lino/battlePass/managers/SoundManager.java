package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoundManager {

    private final BattlePass plugin;
    private final CustomItemManager customItemManager;
    private final Map<UUID, BukkitTask> activeSoundTasks = new ConcurrentHashMap<>();

    public SoundManager(BattlePass plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    public void startItemSound(Player player) {
        UUID uuid = player.getUniqueId();

        if (!plugin.getConfigManager().isCustomItemSoundsEnabled()) {
            return;
        }

        stopItemSound(uuid);

        BukkitTask task = new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopItemSound(uuid);
                    return;
                }

                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (customItemManager.isPremiumPassItem(itemInHand)) {
                    float pitch = 0.8f + (float)(Math.sin(tickCounter * 0.1) * 0.2);
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.3f, pitch);

                    if (tickCounter % 40 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2f, 1.2f);
                    }

                    if (tickCounter % 60 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.15f, 1.5f);
                    }

                } else if (customItemManager.isBattleCoinsItem(itemInHand)) {
                    if (tickCounter % 20 == 0) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 1.8f);
                    }

                    if (tickCounter % 30 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.15f, 1.5f);
                    }

                    if (tickCounter % 15 == 5) {
                        player.playSound(player.getLocation(), Sound.ENTITY_GLOW_SQUID_AMBIENT, 0.1f, 2.0f);
                    }

                } else if (customItemManager.isLevelBoostItem(itemInHand)) {
                    if (tickCounter % 25 == 0) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.15f, 0.8f);
                    }

                    if (tickCounter % 35 == 10) {
                        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.1f, 1.2f);
                    }

                    if (tickCounter % 40 == 20) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1f, 0.6f);
                    }

                    if (tickCounter % 50 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.08f, 1.5f);
                    }

                } else {
                    stopItemSound(uuid);
                    return;
                }

                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        activeSoundTasks.put(uuid, task);
    }

    public void stopItemSound(UUID uuid) {
        BukkitTask task = activeSoundTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void checkAndUpdateSound(Player player) {
        if (!plugin.getConfigManager().isCustomItemSoundsEnabled()) {
            stopItemSound(player.getUniqueId());
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        UUID uuid = player.getUniqueId();

        if (customItemManager.isPremiumPassItem(itemInHand) ||
                customItemManager.isBattleCoinsItem(itemInHand) ||
                customItemManager.isLevelBoostItem(itemInHand)) {
            if (!activeSoundTasks.containsKey(uuid)) {
                startItemSound(player);
            }
        } else {
            stopItemSound(uuid);
        }
    }

    public void stopAllSounds() {
        for (BukkitTask task : activeSoundTasks.values()) {
            task.cancel();
        }
        activeSoundTasks.clear();
    }
}