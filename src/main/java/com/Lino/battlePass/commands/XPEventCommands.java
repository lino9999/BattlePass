package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.XPEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles XP event start and stop commands. */
final class XPEventCommands {

    private final BattlePass plugin;

    XPEventCommands(BattlePass plugin) {
        this.plugin = plugin;
    }

    boolean handleEventCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.usage.event"));
            return true;
        }

        int multiplier = XPEventManager.parseMultiplier(args[1]);
        if (multiplier < 0) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.invalid-multiplier"));
            return true;
        }

        long duration = XPEventManager.parseDuration(args[2]);
        if (duration <= 0) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.invalid-duration"));
            return true;
        }

        plugin.getXpEventManager().startEvent(multiplier, duration);

        String durationFormatted = plugin.getXpEventManager().getTimeRemaining();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.started",
                            "%multiplier%", String.valueOf(multiplier),
                            "%duration%", durationFormatted));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }

        return true;
    }

    boolean handleStopEventCommand(CommandSender sender) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (!plugin.getXpEventManager().isEventActive()) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.not-active"));
            return true;
        }

        plugin.getXpEventManager().stopEvent();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.event.stopped"));
        }

        return true;
    }
}
