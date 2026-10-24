package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.events.BattlePassXPGainEvent;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.ExperienceProgression;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;

/** Synchronous custom-item redemption; registration and cooldowns stay in the listener. */
final class CustomItemRedemptions {
    private final BattlePass plugin;
    private final CustomItemEffects effects;
    private boolean closed;

    CustomItemRedemptions(BattlePass plugin, CustomItemEffects effects) {
        this.plugin = plugin;
        this.effects = effects;
    }

    boolean isActive() { return !closed && !plugin.isStopping() && plugin.isEnabled(); }

    void shutdown() { closed = true; }

    private void consumeHeldItem(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void consumeEntireHeldItem(PlayerInteractEvent event, Player player) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    void handlePremiumPassUse(PlayerInteractEvent event, Player player, ItemStack item) {
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
            effects.playLater(player, i * 2L, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME,
                        0.5f, 0.5f + (index * 0.1f));
            });
        }
    }

    void handleBattleCoinsUse(PlayerInteractEvent event, Player player) {
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

        int amount = data.addBattleCoins(itemInHand.getAmount());
        if (amount == 0) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.coins-full"));
            return;
        }
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        if (amount == itemInHand.getAmount()) {
            consumeEntireHeldItem(event, player);
        } else {
            itemInHand.setAmount(itemInHand.getAmount() - amount);
        }

        player.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.items.coins-redeemed",
                        "%amount%", String.valueOf(amount)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        for (int i = 0; i < Math.min(amount, 10); i++) {
            final int index = i;
            effects.playLater(player, i * 3L, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.8f, 1.2f + (index * 0.1f));
            });
        }
    }

    void handleLevelBoostUse(PlayerInteractEvent event, Player player, BooleanSupplier currentUse) {
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
        int totalXP = (int) Math.min(Integer.MAX_VALUE, (long) amount * 100);
        long revision = data.getProgressRevision();
        HeldItemSnapshot held = new HeldItemSnapshot(player, event.getHand(), itemInHand);

        BattlePassXPGainEvent xpGainEvent = new BattlePassXPGainEvent(player, BattlePassXPGainEvent.XPSource.LEVEL_BOOST, totalXP);
        Bukkit.getPluginManager().callEvent(xpGainEvent);
        if (!isActive() || !currentUse.getAsBoolean() || !player.isOnline()
                || plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) != data
                || data.getProgressRevision() != revision || !held.stillHeld(player)) return;
        maxLevel = plugin.getRewardManager().getMaxLevel();
        if (data.level >= maxLevel) return;
        totalXP = xpGainEvent.isCancelled() ? 0 : Math.max(0, xpGainEvent.getAmount());

        consumeEntireHeldItem(event, player);

        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
        long levelsGained;
        try {
            levelsGained = ExperienceProgression.addWithLimitedFeedback(data, totalXP, xpPerLevel, maxLevel, () -> {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.level-up",
                                "%level%", String.valueOf(data.level)));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            });
        } finally {
            if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == data) {
                plugin.getPlayerDataManager().markForSave(player.getUniqueId());
            }
        }

        player.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.items.xp-boost-used",
                        "%amount%", String.valueOf(totalXP),
                        "%items%", String.valueOf(amount)));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.5f);

        for (int i = 0; i < Math.min(amount, 15); i++) {
            final int index = i;
            effects.playLater(player, i * 2L, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.6f, 0.8f + (index * 0.15f));
            });
        }

        if (levelsGained > 0) {
            int available = plugin.getRewardManager().countAvailableRewards(data);
            if (available > 0) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.new-rewards"));
            }
        }
    }

    void handleXPEventUse(PlayerInteractEvent event, Player player) {
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

        if (!plugin.getXpEventManager().startEvent(2, 3600000)) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.start-failed"));
            return;
        }
        consumeHeldItem(event, player, itemInHand);

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
            effects.playLater(player, i * 3L, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                        1.0f, 0.5f + (index * 0.15f));
            });
        }
    }

}
