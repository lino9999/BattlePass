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
                completions.add("addpremium");
                completions.add("removepremium");
                completions.add("addxp");
                completions.add("removexp");
                completions.add("addcoins");
                completions.add("removecoins");
            }
        } else if (args.length == 2 && sender.hasPermission("battlepass.admin")) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("addpremium") || subCommand.equals("removepremium") ||
                    subCommand.equals("addxp") || subCommand.equals("removexp") ||
                    subCommand.equals("addcoins") || subCommand.equals("removecoins")) {

                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && sender.hasPermission("battlepass.admin")) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("addxp") || subCommand.equals("removexp") ||
                    subCommand.equals("addcoins") || subCommand.equals("removecoins")) {

                completions.add("10");
                completions.add("50");
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}