package com.Lino.battlePass.tasks;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.PlayerDataManager;
import com.Lino.battlePass.managers.RewardManager;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;

/** One notification attempt per current player; commit state before reentrant presentation. */
final class RewardNotifications {
    private final BattlePass plugin;
    private final PlayerDataManager players;
    private final RewardManager rewards;
    private final BooleanSupplier active;

    RewardNotifications(BattlePass plugin, PlayerDataManager players, RewardManager rewards, BooleanSupplier active) {
        this.plugin = plugin;
        this.players = players;
        this.rewards = rewards;
        this.active = active;
    }

    void run() {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!active.getAsBoolean()) return;
            if (!player.isOnline()) continue;
            PlayerData data = players.getPlayerData(player.getUniqueId());
            if (data == null) continue;
            long revision = data.getProgressRevision();
            try { notifyPlayer(player, data, revision); }
            catch (RuntimeException failure) {
                plugin.getLogger().warning("Could not notify rewards for " + player.getUniqueId() + ": " + failure.getMessage());
            }
        }
    }

    private void notifyPlayer(Player player, PlayerData data, long revision) {
        int available = Math.max(0, rewards.countAvailableRewards(data));
        if (!owns(player, data, revision)) return;
        int captured = data.lastNotification;
        int previous = Math.max(0, captured);
        if (available <= previous) {
            if (available != captured) {
                data.lastNotification = available;
                players.markForSave(player.getUniqueId());
            }
            return;
        }
        String message = plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage(
                "messages.rewards-notification", "%amount%", String.valueOf(available - previous));
        if (!owns(player, data, revision) || data.lastNotification != captured) return;
        data.lastNotification = available;
        players.markForSave(player.getUniqueId());
        boolean sent = false;
        try {
            player.sendMessage(message);
            sent = true;
            if (owns(player, data, revision) && data.lastNotification == available) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
        } catch (RuntimeException failure) {
            if (!sent && owns(player, data, revision) && data.lastNotification == available) {
                data.lastNotification = previous;
                players.markForSave(player.getUniqueId());
            }
            throw failure;
        }
    }

    private boolean owns(Player player, PlayerData data, long revision) {
        return active.getAsBoolean() && player.isOnline() && data.getProgressRevision() == revision
                && players.getPlayerData(player.getUniqueId()) == data;
    }
}
