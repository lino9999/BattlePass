package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BattlePassCommand implements CommandExecutor {

    private final BattlePass plugin;

    public BattlePassCommand(BattlePass plugin) {
        this.plugin = plugin;
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
                reloadPlugin(sender);
                return true;

            case "reset":
                if (args.length > 1 && args[1].equalsIgnoreCase("season")) {
                    return handleResetSeason(sender);
                }
                if (sender instanceof Player) {
                    plugin.getGuiManager().openBattlePassGUI((Player) sender, 1);
                }
                return true;

            case "addpremium":
                return handlePremiumCommand(sender, args, true);

            case "removepremium":
                return handlePremiumCommand(sender, args, false);

            case "addxp":
                return handleXPCommand(sender, args, true);

            case "removexp":
                return handleXPCommand(sender, args, false);

            case "addcoins":
                return handleCoinsCommand(sender, args, true);

            case "removecoins":
                return handleCoinsCommand(sender, args, false);

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
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-premium"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-premium"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-xp"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-xp"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.add-coins"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.remove-coins"));
        }
    }

    private void reloadPlugin(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(plugin.getMessageManager().getPrefix() +
                plugin.getMessageManager().getMessage("messages.config-reloaded"));
    }

    private boolean handleResetSeason(CommandSender sender) {
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

    private boolean handlePremiumCommand(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 2) {
            String usage = add ? "messages.usage.add-premium" : "messages.usage.remove-premium";
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage(usage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.player-not-found"));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        data.hasPremium = add;
        plugin.getPlayerDataManager().markForSave(target.getUniqueId());

        if (add) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.premium.given-sender",
                            "%target%", target.getName()));
            target.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.premium.given-target"));
            target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.premium.removed-sender",
                            "%target%", target.getName()));
            target.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.premium.removed-target"));
        }

        return true;
    }

    private boolean handleXPCommand(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            String usage = add ? "messages.usage.add-xp" : "messages.usage.remove-xp";
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage(usage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.player-not-found"));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.amount-must-be-positive"));
                return true;
            }

            PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
            int xpPerLevel = plugin.getConfigManager().getXpPerLevel();

            if (add) {
                data.xp += amount;
                checkLevelUp(target, data, xpPerLevel);
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.xp.added-sender",
                                "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.xp.added-target",
                                "%amount%", String.valueOf(amount)));
            } else {
                int totalXP = (data.level - 1) * xpPerLevel + data.xp;
                totalXP = Math.max(0, totalXP - amount);

                int newLevel = 1;
                while (totalXP >= xpPerLevel && newLevel < 54) {
                    totalXP -= xpPerLevel;
                    newLevel++;
                }
                data.level = newLevel;
                data.xp = totalXP;

                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.xp.removed-sender",
                                "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.xp.removed-target",
                                "%amount%", String.valueOf(amount)));
            }

            plugin.getPlayerDataManager().markForSave(target.getUniqueId());
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.invalid-amount"));
            return true;
        }
    }

    private boolean handleCoinsCommand(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            String usage = add ? "messages.usage.add-coins" : "messages.usage.remove-coins";
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage(usage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.player-not-found"));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.amount-must-be-positive"));
                return true;
            }

            PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());

            if (add) {
                data.battleCoins += amount;
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.coins.added-sender",
                                "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.coins.added-target",
                                "%amount%", String.valueOf(amount)));
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                if (data.battleCoins < amount) {
                    sender.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.coins.insufficient-remove",
                                    "%target%", target.getName(),
                                    "%current%", String.valueOf(data.battleCoins)));
                    return true;
                }

                data.battleCoins -= amount;
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.coins.removed-sender",
                                "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.coins.removed-target",
                                "%amount%", String.valueOf(amount)));
            }

            plugin.getPlayerDataManager().markForSave(target.getUniqueId());
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.invalid-amount"));
            return true;
        }
    }

    private void checkLevelUp(Player player, PlayerData data, int xpPerLevel) {
        boolean leveled = false;

        while (data.xp >= xpPerLevel && data.level < 54) {
            data.xp -= xpPerLevel;
            data.level++;
            data.totalLevels++;
            leveled = true;

            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.level-up",
                            "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = plugin.getRewardManager().countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.new-rewards"));
            }
        }

        if (leveled) {
            plugin.getPlayerDataManager().markForSave(player.getUniqueId());
        }
    }
}