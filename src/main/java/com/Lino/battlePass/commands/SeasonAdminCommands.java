package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles reloads and season or daily mission resets. */
final class SeasonAdminCommands {

    private final BattlePass plugin;

    SeasonAdminCommands(BattlePass plugin) {
        this.plugin = plugin;
    }

    void reloadPlugin(CommandSender sender) {
        plugin.reload();

        plugin.getDatabaseManager().loadCoinsDistributionTime()
                .exceptionally(ex -> null)
                .thenAccept(plugin::startCoinsDistributionTask);

        sender.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.config-reloaded"));
    }

    boolean handleResetSeason(CommandSender sender) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.season.reset-warning"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getMissionManager().forceResetSeason();
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.season.reset-complete"));

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)) {
                    player.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.season.reset-admin",
                                    "%admin%", sender.getName()));
                }
            }
        }, 40L);

        return true;
    }

    boolean handleResetMissions(CommandSender sender) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        plugin.getMissionManager().forceResetMissions();
        sender.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.mission.reset-complete"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(sender)) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.mission.reset-admin",
                                "%admin%", sender.getName()));
            }
        }

        return true;
    }
}
