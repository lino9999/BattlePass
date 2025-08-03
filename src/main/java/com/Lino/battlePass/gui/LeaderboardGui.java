package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
                    if (i == 0) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.first");
                    else if (i == 1) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.second");
                    else if (i == 2) rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.third");
                    else rank = plugin.getMessageManager().getMessage("items.leaderboard-rank.other", "%rank%", String.valueOf(i + 1));

                    skullMeta.setDisplayName(plugin.getMessageManager().getMessage("items.leaderboard-player.name",
                            "%rank%", rank, "%player%", playerName));

                    String status = topPlayer.uuid.equals(player.getUniqueId()) ?
                            plugin.getMessageManager().getMessage("items.leaderboard-status.you") :
                            plugin.getMessageManager().getMessage("items.leaderboard-status.other");

                    List<String> lore = new ArrayList<>();
                    for (String line : plugin.getMessageManager().getMessagesConfig().getStringList("items.leaderboard-player.lore")) {
                        String processedLine = line
                                .replace("%level%", String.valueOf(topPlayer.level))
                                .replace("%total_levels%", String.valueOf(topPlayer.totalLevels))
                                .replace("%xp%", String.valueOf(topPlayer.xp))
                                .replace("%coins%", String.valueOf(topPlayer.battleCoins))
                                .replace("%status%", status);
                        lore.add(GradientColorParser.parse(processedLine));
                    }

                    skullMeta.setLore(lore);
                    skull.setItemMeta(skullMeta);
                    gui.setItem(slots[i], skull);
                }

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
        gui.setItem(40, coinsInfo);
    }
}