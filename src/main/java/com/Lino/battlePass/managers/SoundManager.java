package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoundManager {

    private final BattlePass plugin;
    private final CustomItemManager customItemManager;
    private final Map<UUID, ItemSound> activeSoundTasks = new ConcurrentHashMap<>();

    public SoundManager(BattlePass plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    public void startItemSound(Player player) {
        UUID uuid = player.getUniqueId();
        if (!canPlay(player)) {
            stopForPlayer(player);
            return;
        }
        stopItemSound(uuid);
        ItemSound sound = new ItemSound(player);
        activeSoundTasks.put(uuid, sound);
        try {
            sound.task = Bukkit.getScheduler().runTaskTimer(plugin, sound, 0L, 2L);
            if (sound.stopped) sound.task.cancel();
        } catch (RuntimeException failure) {
            stopOwned(uuid, sound);
            throw failure;
        }
    }

    private boolean canPlay(Player player) {
        return !plugin.isStopping() && plugin.isEnabled() && player.isOnline()
                && plugin.getConfigManager().isCustomItemSoundsEnabled();
    }

    private void stopForPlayer(Player player) {
        ItemSound current = activeSoundTasks.get(player.getUniqueId());
        if (current != null && current.player == player) stopOwned(player.getUniqueId(), current);
    }

    private void stopOwned(UUID uuid, ItemSound sound) {
        if (activeSoundTasks.remove(uuid, sound)) sound.stop();
    }

    public void stopItemSound(UUID uuid) {
        ItemSound sound = activeSoundTasks.remove(uuid);
        if (sound != null) sound.stop();
    }

    public void checkAndUpdateSound(Player player) {
        if (!canPlay(player)) {
            stopForPlayer(player);
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        UUID uuid = player.getUniqueId();
        if (customItemManager.isPremiumPassItem(itemInHand)
                || customItemManager.isBattleCoinsItem(itemInHand)
                || customItemManager.isLevelBoostItem(itemInHand)) {
            ItemSound current = activeSoundTasks.get(uuid);
            if (current == null || current.player != player) startItemSound(player);
        } else {
            stopForPlayer(player);
        }
    }

    public void stopAllSounds() {
        List<ItemSound> previous = List.copyOf(activeSoundTasks.values());
        activeSoundTasks.clear();
        previous.forEach(ItemSound::stop);
    }

    private final class ItemSound implements Runnable {
        private final Player player;
        private final UUID uuid;
        private BukkitTask task;
        private boolean stopped;
        private long tickCounter;

        private ItemSound(Player player) {
            this.player = player;
            uuid = player.getUniqueId();
        }

        private void stop() {
            stopped = true;
            if (task != null) task.cancel();
        }

        @Override public void run() {
            if (stopped || activeSoundTasks.get(uuid) != this) return;
            if (!canPlay(player)) {
                stopOwned(uuid, this);
                return;
            }
            try { render(); }
            catch (RuntimeException failure) {
                stopOwned(uuid, this);
                throw failure;
            }
        }

        private void render() {
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
                stopOwned(uuid, this);
                return;
            }

            tickCounter++;
        }
    }
}
