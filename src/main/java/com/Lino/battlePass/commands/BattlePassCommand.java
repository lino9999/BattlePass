package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BattlePassCommand implements CommandExecutor {

    private final BattlePass plugin;
    private final PlayerAdminCommands players;
    private final RewardAdminCommands rewards;
    private final SeasonAdminCommands seasons;
    private final XPEventCommands events;

    public BattlePassCommand(BattlePass plugin) {
        this.plugin = plugin;
        players = new PlayerAdminCommands(plugin);
        rewards = new RewardAdminCommands(plugin);
        seasons = new SeasonAdminCommands(plugin);
        events = new XPEventCommands(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.player-only"));
                return true;
            }
            plugin.getGuiManager().openBattlePassGUI((Player) sender, 1);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelpMessage(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("battlepass.admin")) {
                    sender.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.no-permission"));
                    return true;
                }
                seasons.reloadPlugin(sender);
                return true;

            case "reset":
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("season")) {
                        return seasons.handleResetSeason(sender);
                    } else if (args[1].equalsIgnoreCase("mission") || args[1].equalsIgnoreCase("missions")) {
                        return seasons.handleResetMissions(sender);
                    }
                }
                if (sender instanceof Player) {
                    plugin.getGuiManager().openBattlePassGUI((Player) sender, 1);
                }
                return true;

            case "event":
                return events.handleEventCommand(sender, args);

            case "stopevent":
                return events.handleStopEventCommand(sender);

            case "edit":
                return rewards.handleEditCommand(sender, args);

            case "resetplayer":
                return players.handleResetPlayer(sender, args);

            case "addpremium":
                return players.handlePremiumCommand(sender, args, true);

            case "removepremium":
                return players.handlePremiumCommand(sender, args, false);

            case "addxp":
                return players.handleXPCommand(sender, args, true);

            case "removexp":
                return players.handleXPCommand(sender, args, false);

            case "addcoins":
                return players.handleCoinsCommand(sender, args, true);

            case "removecoins":
                return players.handleCoinsCommand(sender, args, false);

            case "giveitem":
                return rewards.handleGiveItemCommand(sender, args);

            case "excludefromtop":
                return players.handleExcludeTopCommand(sender, args, true);

            case "includetop":
                return players.handleExcludeTopCommand(sender, args, false);

            default:
                if (sender instanceof Player) {
                    plugin.getGuiManager().openBattlePassGUI((Player) sender, 1);
                }
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.battlepass"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.help"));

        if (sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.reload"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.reset-season"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.reset-missions"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.reset-player"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-premium"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-premium"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-xp"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-xp"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-coins"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-coins"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.giveitem"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.exclude-top"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.include-top"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.edit-rewards-season"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.event"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.stopevent"));
        }
    }
}
