package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardGui extends BaseGui {

    private final Player player;

    public LeaderboardGui(BattlePass plugin, Player player) {
        super(plugin, plugin.getMessageManager().getMessage("gui.leaderboard"), 54);
        this.player = player;
    }

    public void open() {
        Inventory gui = createInventory();

        setupTitleItem(gui);
        setupLeaderboard(gui);
        setupPlayerRank(gui);

        if (plugin.getConfigManager().isShopEnabled()) {
            setupCoinsInfo(gui);
        }

        gui.setItem(49, createBackButton());

        player.openInventory(gui);
    }

    private void setupTitleItem(Inventory gui) {
        ItemStack titleItem = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = titleItem.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.leaderboard-title.name"));

        List<String> lore = new ArrayList<>();
        String coinsTime = plugin.getCoinsDistributionTask() != null ?
                plugin.getCoinsDistributionTask().getTimeUntilNextDistribution() : "Unknown";

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.leaderboard-title.lore")) {
            String processedLine = line
                    .replace("%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd())
                    .replace("%coins_time%", coinsTime);
            lore.add(GradientColorParser.parse(processedLine));
        }

        meta.setLore(lore);
        titleItem.setItemMeta(meta);
        gui.setItem(4, titleItem);
    }

    private void setupLeaderboard(Inventory gui) {
        int leaderboardSize = plugin.getConfigManager().getLeaderboardSize();
        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();

        plugin.getDatabaseManager().getTop10Players().thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Row 2 (slots 9-17), Row 3 (slots 18-26), Row 4 (slots 27+)
                for (int i = 0; i < topPlayers.size() && i < leaderboardSize; i++) {
                    PlayerData topPlayer = topPlayers.get(i);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(topPlayer.uuid);

                    String playerName = offlinePlayer.getName();
                    if (playerName == null) {
                        playerName = "Unknown";
                    }

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                    skullMeta.setOwningPlayer(offlinePlayer);

                    String rank;
                    if (i == 0) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.first");
                    else if (i == 1) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.second");
                    else if (i == 2) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.third");
                    else rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.other", "%rank%", String.valueOf(i + 1));

                    skullMeta.setDisplayName(plugin.getMessageManager().getMessage("items.leaderboard-player.name",
                            "%rank%", rank, "%player%", playerName));

                    String status = topPlayer.uuid.equals(player.getUniqueId()) ?
                            plugin.getMessageManager().getMessage("items.leaderboard-status.you") :
                            plugin.getMessageManager().getMessage("items.leaderboard-status.other");

                    String progressBar = createProgressBar(topPlayer.xp, xpPerLevel);

                    List<String> lore = new ArrayList<>();
                    for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.leaderboard-player.lore")) {
                        String processedLine = line
                                .replace("%level%", String.valueOf(topPlayer.level))
                                .replace("%total_levels%", String.valueOf(topPlayer.totalLevels))
                                .replace("%xp%", String.valueOf(topPlayer.xp))
                                .replace("%xp_needed%", String.valueOf(xpPerLevel))
                                .replace("%progress_bar%", progressBar)
                                .replace("%coins%", String.valueOf(topPlayer.battleCoins))
                                .replace("%status%", status);
                        lore.add(GradientColorParser.parse(processedLine));
                    }

                    skullMeta.setLore(lore);
                    skull.setItemMeta(skullMeta);
                    // Slots: 9-17 (row 2), 18-26 (row 3), 27-35 (row 4)
                    gui.setItem(9 + i, skull);
                }

                if (player.getOpenInventory().getTitle().equals(title)) {
                    player.updateInventory();
                }
            });
        });
    }

    private void setupPlayerRank(Inventory gui) {
        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
        int minLevel = plugin.getConfigManager().getLeaderboardMinLevel();

        plugin.getDatabaseManager().getPlayerRankInfo(player.getUniqueId()).thenAccept(rankInfo -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int rank = rankInfo[0];
                int total = rankInfo[1];

                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (data == null) return;

                ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(plugin.getMessageManager().getMessage("items.player-rank.name"));

                String progressBar = createProgressBar(data.xp, xpPerLevel);
                String lorePath = rank > 0 ? "items.player-rank.lore-ranked" : "items.player-rank.lore-unranked";

                List<String> lore = new ArrayList<>();
                for (String line : plugin.getMessageManager().getMessagesConfig().getStringList(lorePath)) {
                    String processedLine = line
                            .replace("%rank%", rank > 0 ? String.valueOf(rank) : "-")
                            .replace("%total%", String.valueOf(total))
                            .replace("%level%", String.valueOf(data.level))
                            .replace("%xp%", String.valueOf(data.xp))
                            .replace("%xp_needed%", String.valueOf(xpPerLevel))
                            .replace("%progress_bar%", progressBar)
                            .replace("%min_level%", String.valueOf(minLevel));
                    lore.add(GradientColorParser.parse(processedLine));
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
                gui.setItem(31, item);

                if (player.getOpenInventory().getTitle().equals(title)) {
                    player.updateInventory();
                }
            });
        });
    }

    private void setupCoinsInfo(Inventory gui) {
        ItemStack coinsInfo = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = coinsInfo.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getMessage("items.coins-info.name"));

        List<String> lore = new ArrayList<>();
        String nextDistTime = plugin.getCoinsDistributionTask() != null ?
                plugin.getCoinsDistributionTask().getTimeUntilNextDistribution() : "Unknown";

        for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.coins-info.lore")) {
            String processedLine = line.replace("%time%", nextDistTime);
            lore.add(GradientColorParser.parse(processedLine));
        }

        meta.setLore(lore);
        coinsInfo.setItemMeta(meta);
        gui.setItem(34, coinsInfo);
    }

    private String createProgressBar(int current, int max) {
        int totalBars = 10;
        int filled;
        if (max <= 0) {
            filled = 0;
        } else {
            filled = Math.min(totalBars, (int) Math.round((double) current / max * totalBars));
        }
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) bar.append("&a█");
        for (int i = filled; i < totalBars; i++) bar.append("&8░");
        return bar.toString();
    }
}
