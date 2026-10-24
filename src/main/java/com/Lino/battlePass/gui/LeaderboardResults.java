package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.DatabaseManager;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** One asynchronous result bound to its database lifecycle and exact displayed inventory. */
final class LeaderboardResults {
    private final BattlePass plugin;
    private final DatabaseManager database;
    private final Player player;
    private final Inventory gui;

    LeaderboardResults(BattlePass plugin, Player player, Inventory gui) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.player = player;
        this.gui = gui;
    }

    void request() {
        if (!isActive()) return;
        try {
            database.getTop10Players().whenComplete((rows, failure) -> {
                if (!isActive()) return;
                if (failure != null) { fail(failure); return; }
                List<PlayerData> snapshot;
                try { snapshot = rows.stream().limit(10).map(PlayerData::copy).toList(); }
                catch (RuntimeException invalid) { fail(invalid); return; }
                onMain(() -> render(snapshot));
            });
        } catch (RuntimeException failure) { fail(failure); }
    }

    private boolean isActive() {
        return database != null && !plugin.isStopping() && plugin.isEnabled() && plugin.getDatabaseManager() == database;
    }

    private boolean isCurrent() {
        return isActive() && player.isOnline() && player.getOpenInventory().getTopInventory() == gui;
    }

    private void onMain(Runnable action) {
        if (!isActive()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> { if (isCurrent()) action.run(); });
        } catch (RuntimeException rejected) {
            if (isActive()) warn(rejected);
        }
    }

    private void fail(Throwable failure) {
        if (!isActive()) return;
        warn(failure);
        onMain(this::showFailure);
    }

    private void warn(Throwable failure) {
        plugin.getLogger().warning("Could not load leaderboard: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private void showFailure() {
        if (!isCurrent()) return;
        try {
            String message = plugin.getMessageManager().getPrefix()
                    + plugin.getMessageManager().getMessage("messages.leaderboard-unavailable");
            if (isCurrent()) player.sendMessage(message);
        } catch (RuntimeException failure) { warn(failure); }
    }

    private void render(List<PlayerData> topPlayers) {
        try { renderRows(topPlayers); }
        catch (RuntimeException failure) {
            warn(failure);
            showFailure();
        }
    }

    private void renderRows(List<PlayerData> topPlayers) {
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

        for (int i = 0; i < topPlayers.size() && i < 10; i++) {
            if (!isCurrent()) return;
            PlayerData topPlayer = topPlayers.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(topPlayer.uuid);
            if (!isCurrent()) return;

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
            if (!isCurrent()) return;
            gui.setItem(slots[i], skull);
        }
        if (isCurrent()) player.updateInventory();
    }
}
