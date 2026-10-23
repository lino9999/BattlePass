package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.RewardsEditorGui;
import com.Lino.battlePass.managers.SeasonRotationManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;

/** Handles reward editor and custom item delivery commands. */
final class RewardAdminCommands {

    private final BattlePass plugin;

    RewardAdminCommands(BattlePass plugin) {
        this.plugin = plugin;
    }

    boolean handleEditCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2 || !args[1].equalsIgnoreCase("rewards")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.usage.edit-rewards-season"));
            return true;
        }

        if (args.length >= 4 && args[2].equalsIgnoreCase("season")) {
            SeasonRotationManager rotation = plugin.getSeasonRotationManager();
            if (!rotation.isRotationEnabled()) {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.season.rotation-disabled"));
                return true;
            }

            try {
                int seasonNum = Integer.parseInt(args[3]);
                if (!rotation.isValidSeason(seasonNum)) {
                    sender.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.season.invalid-season",
                                    "%max%", String.valueOf(rotation.getTotalSeasons())));
                    return true;
                }

                plugin.getRewardEditorManager().setSeasonEditingContext(player.getUniqueId(), seasonNum);

                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.season.editing-season",
                                "%season%", String.valueOf(seasonNum)));

                new RewardsEditorGui(plugin, player).open();

            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.invalid-amount"));
            }
        } else {
            plugin.getRewardEditorManager().clearSeasonEditingContext(player.getUniqueId());
            new RewardsEditorGui(plugin, player).open();
        }

        return true;
    }

    boolean handleGiveItemCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.usage.giveitem"));
            return true;
        }

        String itemType = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);

        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.player-not-found"));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[3]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.amount-must-be-positive"));
                return true;
            }

            ItemStack item;
            String itemName;

            switch (itemType) {
                case "premium":
                    item = plugin.getCustomItemManager().createPremiumPassItem(amount);
                    itemName = "Premium Pass Voucher";
                    break;
                case "coins":
                    item = plugin.getCustomItemManager().createBattleCoinsItem(amount);
                    itemName = "Battle Coin Token";
                    break;
                case "levelboost":
                    item = plugin.getCustomItemManager().createLevelBoostItem(amount);
                    itemName = "Experience Boost Elixir";
                    break;
                case "xpevent":
                    item = plugin.getCustomItemManager().createXPEventItem(amount);
                    itemName = "XP Event Beacon";
                    break;
                default:
                    sender.sendMessage(plugin.getMessageManager().getPrefix() +
                            plugin.getMessageManager().getMessage("messages.invalid-item-type"));
                    return true;
            }

            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item);

            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.items.given-dropped",
                                "%amount%", String.valueOf(amount),
                                "%item%", itemName,
                                "%target%", target.getName()));
            } else {
                sender.sendMessage(plugin.getMessageManager().getPrefix() +
                        plugin.getMessageManager().getMessage("messages.items.given",
                                "%amount%", String.valueOf(amount),
                                "%item%", itemName,
                                "%target%", target.getName()));
            }

            target.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.items.received",
                            "%amount%", String.valueOf(amount),
                            "%item%", itemName));
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getPrefix() +
                    plugin.getMessageManager().getMessage("messages.invalid-amount"));
            return true;
        }
    }
}
