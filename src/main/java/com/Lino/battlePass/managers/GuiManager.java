package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class GuiManager {

    private final BattlePass plugin;
    private final PlayerDataManager playerDataManager;
    private final MissionManager missionManager;
    private final RewardManager rewardManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;

    private final Map<Integer, Integer> currentPages = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();

    public GuiManager(BattlePass plugin, PlayerDataManager playerDataManager, MissionManager missionManager,
                      RewardManager rewardManager, MessageManager messageManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.missionManager = missionManager;
        this.rewardManager = rewardManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    public void openBattlePassGUI(Player player, int page) {
        String title = messageManager.getMessage("gui.battlepass", "%page%", String.valueOf(page));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = playerDataManager.getPlayerData(player.getUniqueId());

        currentPages.put(player.getEntityId(), page);

        ItemStack info = getCachedItem("info_star", () -> {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.progress.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta infoMeta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        String premiumStatus = data.hasPremium ?
                messageManager.getMessage("items.premium-status.active") :
                messageManager.getMessage("items.premium-status.inactive");

        for (String line : messageManager.getMessagesConfig().getStringList("items.progress.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%level%", String.valueOf(data.level))
                    .replace("%xp%", String.valueOf(data.xp))
                    .replace("%xp_needed%", String.valueOf(configManager.getXpPerLevel()))
                    .replace("%premium_status%", premiumStatus)
                    .replace("%season_time%", missionManager.getTimeUntilSeasonEnd())));
        }
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        int startLevel = (page - 1) * 9 + 1;
        int endLevel = Math.min(startLevel + 8, 54);

        Map<Integer, List<Reward>> premiumRewardsByLevel = rewardManager.getPremiumRewardsByLevel();
        Map<Integer, List<Reward>> freeRewardsByLevel = rewardManager.getFreeRewardsByLevel();

        for (int i = 0; i <= 8 && startLevel + i <= 54; i++) {
            int level = startLevel + i;

            List<Reward> premiumLevel = premiumRewardsByLevel.get(level);
            if (premiumLevel != null && !premiumLevel.isEmpty()) {
                ItemStack premiumItem = createRewardItem(premiumLevel, level, data,
                        data.hasPremium, true);
                gui.setItem(9 + i, premiumItem);
            }

            List<Reward> freeLevel = freeRewardsByLevel.get(level);
            if (freeLevel != null && !freeLevel.isEmpty()) {
                ItemStack freeItem = createRewardItem(freeLevel, level, data, true, false);
                gui.setItem(27 + i, freeItem);
            }
        }

        ItemStack separator = getCachedItem("separator", () -> {
            ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.separator.name"));
            item.setItemMeta(meta);
            return item;
        });

        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }

        if (page > 1) {
            gui.setItem(45, createNavigationItem(false, page - 1));
        }

        if (endLevel < 54) {
            gui.setItem(53, createNavigationItem(true, page + 1));
        }

        ItemStack missions = getCachedItem("missions_book", () -> {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.missions-button.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta missionsMeta = missions.getItemMeta();
        List<String> missionsLore = new ArrayList<>();
        for (String line : messageManager.getMessagesConfig().getStringList("items.missions-button.lore")) {
            missionsLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", missionManager.getTimeUntilReset())));
        }
        missionsMeta.setLore(missionsLore);
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = getCachedItem("leaderboard_helmet", () -> {
            ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.leaderboard-button.name"));
            List<String> lboardLore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList("items.leaderboard-button.lore")) {
                lboardLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lboardLore);
            item.setItemMeta(meta);
            return item;
        });

        gui.setItem(48, leaderboard);
        player.openInventory(gui);
    }

    public void openLeaderboardGUI(Player player) {
        String title = messageManager.getMessage("gui.leaderboard");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack titleItem = getCachedItem("leaderboard_title", () -> {
            ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.leaderboard-title.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta titleMeta = titleItem.getItemMeta();
        List<String> titleLore = new ArrayList<>();
        for (String line : messageManager.getMessagesConfig().getStringList("items.leaderboard-title.lore")) {
            titleLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%season_time%", missionManager.getTimeUntilSeasonEnd())));
        }
        titleMeta.setLore(titleLore);
        titleItem.setItemMeta(titleMeta);
        gui.setItem(4, titleItem);

        plugin.getDatabaseManager().getTop10Players().thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

                for (int i = 0; i < topPlayers.size() && i < 10; i++) {
                    PlayerData topPlayer = topPlayers.get(i);
                    String playerName = Bukkit.getOfflinePlayer(topPlayer.uuid).getName();

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(topPlayer.uuid));

                    String rank;
                    if (i == 0) rank = messageManager.getMessage("items.leaderboard-rank.first");
                    else if (i == 1) rank = messageManager.getMessage("items.leaderboard-rank.second");
                    else if (i == 2) rank = messageManager.getMessage("items.leaderboard-rank.third");
                    else rank = messageManager.getMessage("items.leaderboard-rank.other", "%rank%", String.valueOf(i + 1));

                    skullMeta.setDisplayName(messageManager.getMessage("items.leaderboard-player.name",
                            "%rank%", rank, "%player%", playerName));

                    String status = topPlayer.uuid.equals(player.getUniqueId()) ?
                            messageManager.getMessage("items.leaderboard-status.you") :
                            messageManager.getMessage("items.leaderboard-status.other");

                    List<String> skullLore = new ArrayList<>();
                    for (String line : messageManager.getMessagesConfig().getStringList("items.leaderboard-player.lore")) {
                        skullLore.add(ChatColor.translateAlternateColorCodes('&', line
                                .replace("%level%", String.valueOf(topPlayer.level))
                                .replace("%total_levels%", String.valueOf(topPlayer.totalLevels))
                                .replace("%xp%", String.valueOf(topPlayer.xp))
                                .replace("%status%", status)));
                    }
                    skullMeta.setLore(skullLore);
                    skull.setItemMeta(skullMeta);
                    gui.setItem(slots[i], skull);
                }

                if (player.getOpenInventory().getTitle().equals(title)) {
                    player.updateInventory();
                }
            });
        });

        ItemStack back = getCachedItem("back_barrier", () -> {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.back-button.name"));
            List<String> backLore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList("items.back-button.lore")) {
                backLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(backLore);
            item.setItemMeta(meta);
            return item;
        });

        gui.setItem(49, back);
        player.openInventory(gui);
    }

    public void openMissionsGUI(Player player) {
        String title = messageManager.getMessage("gui.missions");
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = playerDataManager.getPlayerData(player.getUniqueId());

        ItemStack timer = getCachedItem("mission_timer", () -> {
            ItemStack item = new ItemStack(Material.CLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.mission-timer.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta timerMeta = timer.getItemMeta();
        List<String> timerLore = new ArrayList<>();
        for (String line : messageManager.getMessagesConfig().getStringList("items.mission-timer.lore")) {
            timerLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", missionManager.getTimeUntilReset())));
        }
        timerMeta.setLore(timerLore);
        timer.setItemMeta(timerMeta);
        gui.setItem(4, timer);

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        List<Mission> currentMissions = missionManager.getDailyMissions();

        for (int i = 0; i < currentMissions.size() && i < slots.length; i++) {
            Mission mission = currentMissions.get(i);
            String key = mission.name.toLowerCase().replace(" ", "_");
            int progress = data.missionProgress.getOrDefault(key, 0);
            boolean completed = progress >= mission.required;

            ItemStack missionItem = new ItemStack(completed ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta missionMeta = missionItem.getItemMeta();

            String itemName = completed ? "items.mission-completed.name" : "items.mission-in-progress.name";
            String itemLore = completed ? "items.mission-completed.lore" : "items.mission-in-progress.lore";

            missionMeta.setDisplayName(messageManager.getMessage(itemName, "%mission%", mission.name));

            List<String> missionLore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList(itemLore)) {
                missionLore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%progress%", String.valueOf(progress))
                        .replace("%required%", String.valueOf(mission.required))
                        .replace("%reward_xp%", String.valueOf(mission.xpReward))
                        .replace("%reset_time%", missionManager.getTimeUntilReset())));
            }
            missionMeta.setLore(missionLore);
            missionItem.setItemMeta(missionMeta);
            gui.setItem(slots[i], missionItem);
        }

        ItemStack back = getCachedItem("back_barrier", () -> {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.back-button.name"));
            List<String> backLore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList("items.back-button.lore")) {
                backLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(backLore);
            item.setItemMeta(meta);
            return item;
        });

        gui.setItem(49, back);
        player.openInventory(gui);
    }

    private ItemStack createRewardItem(List<Reward> rewards, int level, PlayerData data,
                                       boolean hasAccess, boolean isPremium) {
        Set<Integer> claimedSet = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;
        ItemStack item;
        ItemMeta meta;

        String rewardType = messageManager.getMessage(isPremium ? "reward-types.premium" : "reward-types.free");

        if (data.level >= level && hasAccess && !claimedSet.contains(level)) {
            item = new ItemStack(Material.CHEST_MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.reward-available.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-available");
            meta.setLore(lore);

        } else if (claimedSet.contains(level)) {
            Reward displayReward = rewards.stream()
                    .filter(r -> r.material != Material.COMMAND_BLOCK)
                    .findFirst()
                    .orElse(rewards.get(0));

            item = new ItemStack(displayReward.material, displayReward.amount);
            meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.reward-claimed.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-claimed");
            meta.setLore(lore);

        } else if (!hasAccess && isPremium) {
            item = new ItemStack(Material.MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(level)));

            List<String> lore = createRewardLore(rewards, "items.reward-premium-locked");
            meta.setLore(lore);

        } else {
            item = new ItemStack(Material.MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(messageManager.getMessage("items.reward-level-locked.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-level-locked");
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private List<String> createRewardLore(List<Reward> rewards, String configPath) {
        List<String> lore = new ArrayList<>();

        for (String line : messageManager.getMessagesConfig().getStringList(configPath + ".lore-header")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        for (Reward r : rewards) {
            if (r.command != null) {
                lore.add(messageManager.getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
            } else {
                lore.add(messageManager.getMessage("messages.rewards.item-reward",
                        "%amount%", String.valueOf(r.amount),
                        "%item%", formatMaterial(r.material)));
            }
        }

        for (String line : messageManager.getMessagesConfig().getStringList(configPath + ".lore-footer")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%level%", String.valueOf(rewards.get(0).level))
                    .replace("%season_time%", missionManager.getTimeUntilSeasonEnd())));
        }

        return lore;
    }

    private ItemStack createNavigationItem(boolean next, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (next) {
            meta.setDisplayName(messageManager.getMessage("items.next-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList("items.next-page.lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(targetPage))));
            }
            meta.setLore(lore);
        } else {
            meta.setDisplayName(messageManager.getMessage("items.previous-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : messageManager.getMessagesConfig().getStringList("items.previous-page.lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(targetPage))));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getCachedItem(String key, Supplier<ItemStack> supplier) {
        return itemCache.computeIfAbsent(key, k -> supplier.get()).clone();
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    public void clearCache() {
        itemCache.clear();
    }

    public Map<Integer, Integer> getCurrentPages() {
        return currentPages;
    }
}