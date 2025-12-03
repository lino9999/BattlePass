package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class CustomItemsListener implements Listener {

    private final BattlePass plugin;

    public CustomItemsListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        if (plugin.getCustomItemManager().isPremiumPassItem(item)) {
            handlePremiumPassUse(event, player, item);
        } else if (plugin.getCustomItemManager().isBattleCoinsItem(item)) {
            handleBattleCoinsUse(event, player);
        } else if (plugin.getCustomItemManager().isLevelBoostItem(item)) {
            handleLevelBoostUse(event, player);
        }
    }

    private void handlePremiumPassUse(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.hasPremium) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.already-have-premium"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        data.hasPremium = true;
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            if (player.getInventory().getItemInMainHand().equals(item)) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }

        player.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.items.premium-activated"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);

        for (int i = 0; i < 20; i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME,
                        0.5f, 0.5f + (index * 0.1f));
            }, i * 2L);
        }
    }

    private void handleBattleCoinsUse(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!plugin.getCustomItemManager().isBattleCoinsItem(itemInHand)) {
            return;
        }

        int amount = itemInHand.getAmount();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        data.battleCoins += amount;
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        player.getInventory().setItemInMainHand(null);

        player.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.items.coins-redeemed",
                        "%amount%", String.valueOf(amount)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        for (int i = 0; i < Math.min(amount, 10); i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.8f, 1.2f + (index * 0.1f));
            }, i * 3L);
        }
    }

    private void handleLevelBoostUse(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!plugin.getCustomItemManager().isLevelBoostItem(itemInHand)) {
            return;
        }

        int maxLevel = plugin.getRewardManager().getMaxLevel();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (data.level >= maxLevel) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.max-level-reached"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int amount = itemInHand.getAmount();
        int totalXP = amount * 100;

        player.getInventory().setItemInMainHand(null);

        data.xp += totalXP;

        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
        int levelsGained = 0;

        while (data.xp >= xpPerLevel && data.level < maxLevel) {
            data.xp -= xpPerLevel;
            data.level++;
            data.totalLevels++;
            levelsGained++;

            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.level-up",
                            "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        player.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.items.xp-boost-used",
                        "%amount%", String.valueOf(totalXP),
                        "%items%", String.valueOf(amount)));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.5f);

        for (int i = 0; i < Math.min(amount, 15); i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.6f, 0.8f + (index * 0.15f));
            }, i * 2L);
        }

        if (levelsGained > 0) {
            int available = plugin.getRewardManager().countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.new-rewards"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getSoundManager().checkAndUpdateSound(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getSoundManager().checkAndUpdateSound(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getSoundManager().checkAndUpdateSound(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getSoundManager().checkAndUpdateSound(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getSoundManager().checkAndUpdateSound(player);
            }, 1L);
        }
    }
}