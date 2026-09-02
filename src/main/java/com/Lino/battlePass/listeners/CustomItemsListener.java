package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomItemsListener implements Listener {

    private final BattlePass plugin;
    private final Map<UUID, Long> lastCustomItemUse = new ConcurrentHashMap<>();

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

        if (!isUsableHand(event, player)) return;

        boolean premium = plugin.getCustomItemManager().isPremiumPassItem(item);
        boolean coins = !premium && plugin.getCustomItemManager().isBattleCoinsItem(item);
        boolean boost = !premium && !coins && plugin.getCustomItemManager().isLevelBoostItem(item);
        boolean xpEvent = !premium && !coins && !boost && plugin.getCustomItemManager().isXPEventItem(item);

        if (!premium && !coins && !boost && !xpEvent) return;

        long now = System.currentTimeMillis();
        Long last = lastCustomItemUse.get(player.getUniqueId());
        if (last != null && now - last < 50) return;
        lastCustomItemUse.put(player.getUniqueId(), now);

        if (premium) {
            handlePremiumPassUse(event, player, item);
        } else if (coins) {
            handleBattleCoinsUse(event, player);
        } else if (boost) {
            handleLevelBoostUse(event, player);
        } else {
            handleXPEventUse(event, player);
        }
    }

    private boolean isUsableHand(PlayerInteractEvent event, Player player) {
        if (event.getHand() == EquipmentSlot.HAND) return true;
        return player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }

    private void consumeHeldItem(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void handlePremiumPassUse(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.data-loading"));
            return;
        }

        if (data.hasPremium) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.already-have-premium"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        data.hasPremium = true;
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        consumeHeldItem(event, player, item);

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

        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || !plugin.getCustomItemManager().isBattleCoinsItem(itemInHand)) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.data-loading"));
            return;
        }

        int amount = itemInHand.getAmount();
        data.battleCoins += amount;
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        consumeHeldItem(event, player, itemInHand);

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

        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || !plugin.getCustomItemManager().isLevelBoostItem(itemInHand)) {
            return;
        }

        int maxLevel = plugin.getRewardManager().getMaxLevel();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.data-loading"));
            return;
        }

        if (data.level >= maxLevel) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.max-level-reached"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int amount = itemInHand.getAmount();
        int totalXP = amount * 100;

        consumeHeldItem(event, player, itemInHand);

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
            int available = plugin.getRewardManager().countAvailableRewards(data);
            if (available > 0) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.new-rewards"));
            }
        }
    }

    private void handleXPEventUse(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);

        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || !plugin.getCustomItemManager().isXPEventItem(itemInHand)) {
            return;
        }

        if (plugin.getXpEventManager().isEventActive()) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.xp-event-already-active"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        consumeHeldItem(event, player, itemInHand);

        plugin.getXpEventManager().startEvent(2, 3600000);

        String duration = plugin.getXpEventManager().getTimeRemaining();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.xp-event-activated",
                            "%player%", player.getName(),
                            "%duration%", duration));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
        }

        for (int i = 0; i < 10; i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                        1.0f, 0.5f + (index * 0.15f));
            }, i * 3L);
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