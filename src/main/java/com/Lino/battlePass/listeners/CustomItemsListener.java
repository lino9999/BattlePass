package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
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
    private final CustomItemEffects effects;
    private final CustomItemRedemptions redemptions;

    public CustomItemsListener(BattlePass plugin) {
        this.plugin = plugin;
        this.effects = new CustomItemEffects(plugin);
        this.redemptions = new CustomItemRedemptions(plugin, effects);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plugin.isStopping() || !plugin.isEnabled()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        boolean premium = plugin.getCustomItemManager().isPremiumPassItem(item);
        boolean coins = !premium && plugin.getCustomItemManager().isBattleCoinsItem(item);
        boolean boost = !premium && !coins && plugin.getCustomItemManager().isLevelBoostItem(item);
        boolean xpEvent = !premium && !coins && !boost && plugin.getCustomItemManager().isXPEventItem(item);

        if (!premium && !coins && !boost && !xpEvent) return;

        event.setCancelled(true);

        if (!isUsableHand(event, player)) return;

        long now = System.currentTimeMillis();
        Long last = lastCustomItemUse.get(player.getUniqueId());
        if (last != null && now - last < 50) return;
        lastCustomItemUse.put(player.getUniqueId(), now);

        if (premium) {
            redemptions.handlePremiumPassUse(event, player, item);
        } else if (coins) {
            redemptions.handleBattleCoinsUse(event, player);
        } else if (boost) {
            redemptions.handleLevelBoostUse(event, player);
        } else {
            redemptions.handleXPEventUse(event, player);
        }
    }

    private boolean isUsableHand(PlayerInteractEvent event, Player player) {
        if (event.getHand() == EquipmentSlot.HAND) return true;
        return player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        effects.checkNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        effects.checkNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) effects.checkNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        effects.checkNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) effects.checkNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCustomItemUse.remove(uuid);
        effects.cleanup(uuid);
    }

    public void shutdown() {
        effects.shutdown();
        lastCustomItemUse.clear();
    }
}
