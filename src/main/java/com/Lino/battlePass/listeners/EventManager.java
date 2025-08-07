package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager implements Listener {

    private final BattlePass plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playTimeStart = new ConcurrentHashMap<>();
    private final Set<Material> oreTypes = EnumSet.noneOf(Material.class);
    private final Map<UUID, Map<Location, Long>> recentlyPlacedBlocks = new ConcurrentHashMap<>();

    private final NamespacedKey navigationKey;

    public EventManager(BattlePass plugin) {
        this.plugin = plugin;
        this.navigationKey = new NamespacedKey(plugin, "navigation");
        initializeOreTypes();
    }

    private void initializeOreTypes() {
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
                oreTypes.add(mat);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        playTimeStart.put(uuid, System.currentTimeMillis());
        lastLocations.put(uuid, player.getLocation());
        recentlyPlacedBlocks.put(uuid, new HashMap<>());

        plugin.getPlayerDataManager().loadPlayer(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (data != null) {
                    int available = plugin.getRewardManager().countAvailableRewards(player, data);
                    if (available > 0) {
                        player.sendMessage(plugin.getMessageManager().getPrefix() +
                                plugin.getMessageManager().getMessage("messages.rewards-available",
                                        "%amount%", String.valueOf(available)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                plugin.getSoundManager().checkAndUpdateSound(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Long startTime = playTimeStart.remove(uuid);
        if (startTime != null) {
            long playTime = (System.currentTimeMillis() - startTime) / 60000;
            if (playTime > 0) {
                plugin.getMissionManager().progressMission(event.getPlayer(), "PLAY_TIME", "ANY", (int) playTime);
            }
        }

        plugin.getSoundManager().stopItemSound(uuid);
        plugin.getMissionManager().clearPlayerActionbars(uuid);
        plugin.getPlayerDataManager().removePlayer(uuid);
        plugin.getGuiManager().getCurrentPages().remove(event.getPlayer().getEntityId());
        lastLocations.remove(uuid);
        recentlyPlacedBlocks.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastLocations.put(uuid, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location last = lastLocations.get(uuid);
        Location toLoc = event.getTo();

        if (last != null) {
            if (last.getWorld() == null || toLoc.getWorld() == null ||
                    !last.getWorld().getName().equals(toLoc.getWorld().getName())) {
                lastLocations.put(uuid, toLoc);
                return;
            }

            try {
                double distance = last.distance(toLoc);
                if (distance >= 1 && distance < 100) {
                    plugin.getMissionManager().progressMission(player, "WALK_DISTANCE", "ANY", (int) distance);
                }
            } catch (IllegalArgumentException e) {
            }
            lastLocations.put(uuid, toLoc);
        } else {
            lastLocations.put(uuid, toLoc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getMissionManager().progressMission(event.getEntity(), "DEATH", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            plugin.getMissionManager().progressMission(player, "DAMAGE_DEALT", "ANY", (int) event.getDamage());
        }

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            plugin.getMissionManager().progressMission(player, "DAMAGE_TAKEN", "ANY", (int) event.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player) {
            Player player = (Player) event.getOwner();
            String entityType = event.getEntity().getType().name();
            plugin.getMissionManager().progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Player player = event.getPlayer();
            plugin.getMissionManager().progressMission(player, "TRADE_VILLAGER", "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        plugin.getMissionManager().progressMission(event.getEnchanter(), "ENCHANT_ITEM", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            Player player = event.getPlayer();
            if (event.getCaught() instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item item = (org.bukkit.entity.Item) event.getCaught();
                String itemType = item.getItemStack().getType().name();
                plugin.getMissionManager().progressMission(player, "FISH_ITEM", itemType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack result = event.getRecipe().getResult();
            String itemType = result.getType().name();

            int crafted = result.getAmount();
            if (event.isShiftClick()) {
                int minStack = 64;
                for (ItemStack ingredient : event.getInventory().getMatrix()) {
                    if (ingredient != null && ingredient.getType() != Material.AIR) {
                        minStack = Math.min(minStack, ingredient.getAmount());
                    }
                }
                crafted = crafted * minStack;
            }

            plugin.getMissionManager().progressMission(player, "CRAFT_ITEM", itemType, crafted);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Location blockLoc = event.getBlock().getLocation();
        UUID uuid = player.getUniqueId();

        Map<Location, Long> playerPlacedBlocks = recentlyPlacedBlocks.get(uuid);
        if (playerPlacedBlocks == null) {
            playerPlacedBlocks = new HashMap<>();
            recentlyPlacedBlocks.put(uuid, playerPlacedBlocks);
        }

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();

            if (event.getEntity() instanceof Player) {
                plugin.getMissionManager().progressMission(killer, "KILL_PLAYER", "PLAYER", 1);
            } else {
                String entityType = event.getEntityType().name();
                plugin.getMissionManager().progressMission(killer, "KILL_MOB", entityType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Material mat = event.getBlock().getType();

        plugin.getMissionManager().progressMission(player, "BREAK_BLOCK", blockType, 1);

        if (event.isDropItems() && oreTypes.contains(mat)) {
            plugin.getMissionManager().progressMission(player, "MINE_BLOCK", blockType, 1);
        }
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
                player.getInventory().remove(item);
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

        } else if (plugin.getCustomItemManager().isBattleCoinsItem(item)) {
            event.setCancelled(true);

            int amount = item.getAmount();
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            data.battleCoins += amount;
            plugin.getPlayerDataManager().markForSave(player.getUniqueId());

            player.getInventory().remove(item);

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        boolean isBattlePassGUI = false;
        for (int i = 1; i <= 6; i++) {
            if (title.equals(plugin.getMessageManager().getMessage("gui.battlepass", "%page%", String.valueOf(i)))) {
                isBattlePassGUI = true;
                break;
            }
        }

        if (!isBattlePassGUI && !title.equals(plugin.getMessageManager().getMessage("gui.leaderboard")) &&
                !title.equals(plugin.getMessageManager().getMessage("gui.missions")) &&
                !title.equals(plugin.getMessageManager().getMessage("gui.shop"))) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (isBattlePassGUI) {
            handleBattlePassClick(player, clicked, event.getSlot());
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.leaderboard"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                plugin.getGuiManager().openBattlePassGUI(player, page);
            }
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.missions"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
                plugin.getGuiManager().openBattlePassGUI(player, page);
            }
        } else if (title.equals(plugin.getMessageManager().getMessage("gui.shop"))) {
            handleShopClick(player, clicked, event.getSlot());
        }
    }

    private void handleBattlePassClick(Player player, ItemStack clicked, int slot) {
        int currentPage = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);

        if (clicked.getType() == Material.ARROW && clicked.hasItemMeta()) {
            var meta = clicked.getItemMeta();
            if (meta.getPersistentDataContainer().has(navigationKey, PersistentDataType.STRING)) {
                String action = meta.getPersistentDataContainer().get(navigationKey, PersistentDataType.STRING);
                if ("previous".equals(action) && currentPage > 1) {
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage - 1);
                } else if ("next".equals(action) && currentPage < 6) {
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage + 1);
                }
                return;
            }
        }

        switch (clicked.getType()) {
            case BOOK:
                plugin.getGuiManager().openMissionsGUI(player);
                break;

            case GOLDEN_HELMET:
                plugin.getGuiManager().openLeaderboardGUI(player);
                break;

            case GOLD_INGOT:
                plugin.getGuiManager().openShopGUI(player);
                break;

            case SUNFLOWER:
                handleDailyRewardClaim(player, currentPage);
                break;

            case CHEST:
                handleRewardClaim(player, slot, currentPage);
                break;
        }
    }

    private void handleShopClick(Player player, ItemStack clicked, int slot) {
        if (clicked.getType() == Material.BARRIER) {
            int page = plugin.getGuiManager().getCurrentPages().getOrDefault(player.getEntityId(), 1);
            plugin.getGuiManager().openBattlePassGUI(player, page);
            return;
        }

        if (slot == 4) return;

        plugin.getShopManager().purchaseItem(player, slot);
    }

    private void handleRewardClaim(Player player, int slot, int currentPage) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int startLevel = (currentPage - 1) * 9 + 1;

        if (slot >= 9 && slot <= 17) {
            int index = slot - 9;
            int level = startLevel + index;

            if (!data.hasPremium) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.premium.required"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            List<com.Lino.battlePass.models.Reward> levelRewards =
                    plugin.getRewardManager().getPremiumRewardsByLevel().get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                    plugin.getRewardManager().claimRewards(player, data, levelRewards, level, true);
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.rewards.cannot-claim"));
                }
            }

        } else if (slot >= 27 && slot <= 35) {
            int index = slot - 27;
            int level = startLevel + index;

            List<com.Lino.battlePass.models.Reward> levelRewards =
                    plugin.getRewardManager().getFreeRewardsByLevel().get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                    plugin.getRewardManager().claimRewards(player, data, levelRewards, level, false);
                    plugin.getGuiManager().openBattlePassGUI(player, currentPage);
                }
            }
        }
    }

    private void handleDailyRewardClaim(Player player, int currentPage) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long now = System.currentTimeMillis();
        long dayInMillis = 24 * 60 * 60 * 1000;

        if (now - data.lastDailyReward >= dayInMillis) {
            int xpReward = plugin.getConfigManager().getDailyRewardXP();
            data.xp += xpReward;
            data.lastDailyReward = now;

            int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
            boolean leveled = false;

            while (data.xp >= xpPerLevel && data.level < 54) {
                data.xp -= xpPerLevel;
                data.level++;
                data.totalLevels++;
                leveled = true;

                player.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage("messages.level-up",
                        "%level%", String.valueOf(data.level)));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                int available = plugin.getRewardManager().countAvailableRewards(player, data);
                if (available > 0) {
                    player.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage("messages.new-rewards"));
                }
            }

            plugin.getPlayerDataManager().markForSave(player.getUniqueId());

            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.daily-reward.claimed",
                            "%amount%", String.valueOf(xpReward)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            plugin.getGuiManager().openBattlePassGUI(player, currentPage);
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.daily-reward.already-claimed"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    public Map<UUID, Long> getPlayTimeStart() {
        return playTimeStart;
    }

    public NamespacedKey getNavigationKey() {
        return navigationKey;
    }
}