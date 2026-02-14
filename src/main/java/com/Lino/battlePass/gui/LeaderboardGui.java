package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
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
        super(plugin, plugin.getMessageManager().getGuiMessage("gui.leaderboard"), 54);
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
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.leaderboard-title.name"));

        String coinsTime = plugin.getCoinsDistributionTask() != null
                ? plugin.getCoinsDistributionTask().getTimeUntilNextDistribution()
                : plugin.getMessageManager().getMessage("time.unknown");

        meta.setLore(plugin.getMessageManager().getGuiMessages("items.leaderboard-title.lore",
                "%season_time%", plugin.getMissionManager().getTimeUntilSeasonEnd(),
                "%coins_time%", coinsTime));

        titleItem.setItemMeta(meta);
        gui.setItem(4, titleItem);
    }

    private void setupLeaderboard(Inventory gui) {
        plugin.getDatabaseManager().getTop10Players().thenAccept(topPlayers ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

                    for (int i = 0; i < topPlayers.size() && i < 10; i++) {
                        PlayerData topPlayer = topPlayers.get(i);
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(topPlayer.uuid);

                        String playerName = offlinePlayer.getName();
                        if (playerName == null) {
                            playerName = plugin.getMessageManager().getMessage("time.unknown");
                        }

                        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                        skullMeta.setOwningPlayer(offlinePlayer);

                        String rank;
                        if (i == 0) {
                            rank = plugin.getMessageManager().getGuiMessage("items.leaderboard-rank.first");
                        } else if (i == 1) {
                            rank = plugin.getMessageManager().getGuiMessage("items.leaderboard-rank.second");
                        } else if (i == 2) {
                            rank = plugin.getMessageManager().getGuiMessage("items.leaderboard-rank.third");
                        } else {
                            rank = plugin.getMessageManager().getGuiMessage("items.leaderboard-rank.other", "%rank%", String.valueOf(i + 1));
                        }

                        skullMeta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.leaderboard-player.name",
                                "%rank%", rank,
                                "%player%", playerName));

                        String status = topPlayer.uuid.equals(player.getUniqueId())
                                ? plugin.getMessageManager().getGuiMessage("items.leaderboard-status.you")
                                : plugin.getMessageManager().getGuiMessage("items.leaderboard-status.other");

                        List<String> lore = new ArrayList<>(plugin.getMessageManager().getGuiMessages("items.leaderboard-player.lore",
                                "%level%", String.valueOf(topPlayer.level),
                                "%total_levels%", String.valueOf(topPlayer.totalLevels),
                                "%xp%", String.valueOf(topPlayer.xp),
                                "%coins%", String.valueOf(topPlayer.battleCoins),
                                "%status%", status));

                        skullMeta.setLore(lore);
                        skull.setItemMeta(skullMeta);
                        gui.setItem(slots[i], skull);
                    }

                    if (player.getOpenInventory().getTitle().equals(title)) {
                        player.updateInventory();
                    }
                }));
    }

    private void setupCoinsInfo(Inventory gui) {
        ItemStack coinsInfo = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = coinsInfo.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.coins-info.name"));

        String nextDistTime = plugin.getCoinsDistributionTask() != null
                ? plugin.getCoinsDistributionTask().getTimeUntilNextDistribution()
                : plugin.getMessageManager().getMessage("time.unknown");

        meta.setLore(plugin.getMessageManager().getGuiMessages("items.coins-info.lore",
                "%time%", nextDistTime));

        coinsInfo.setItemMeta(meta);
        gui.setItem(40, coinsInfo);
    }
}
