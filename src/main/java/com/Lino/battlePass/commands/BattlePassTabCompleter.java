package com.Lino.battlePass.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BattlePassTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");

            if (sender.hasPermission("battlepass.admin")) {
                completions.add("reload");
                completions.add("reset");
                completions.add("resetplayer");
                completions.add("addpremium");
                completions.add("removepremium");
                completions.add("addxp");
                completions.add("removexp");
                completions.add("addcoins");
                completions.add("removecoins");
                completions.add("giveitem");
                completions.add("excludefromtop");
                completions.add("includetop");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset") && sender.hasPermission("battlepass.admin")) {
                completions.add("season");
                completions.add("mission");
                completions.add("missions");
            } else if (args[0].equalsIgnoreCase("giveitem") && sender.hasPermission("battlepass.admin")) {
                completions.add("premium");
                completions.add("coins");
                completions.add("levelboost");
            } else if (sender.hasPermission("battlepass.admin")) {
                String subCommand = args[0].toLowerCase();

                if (subCommand.equals("addpremium") || subCommand.equals("removepremium") ||
                        subCommand.equals("addxp") || subCommand.equals("removexp") ||
                        subCommand.equals("addcoins") || subCommand.equals("removecoins") ||
                        subCommand.equals("excludefromtop") || subCommand.equals("includetop") ||
                        subCommand.equals("resetplayer")) {

                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
                }
            }
        } else if (args.length == 3 && sender.hasPermission("battlepass.admin")) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("giveitem")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            } else if (subCommand.equals("addxp") || subCommand.equals("removexp") ||
                    subCommand.equals("addcoins") || subCommand.equals("removecoins")) {

                completions.add("10");
                completions.add("50");
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            }
        } else if (args.length == 4 && sender.hasPermission("battlepass.admin")) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("giveitem")) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("32");
                completions.add("64");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}