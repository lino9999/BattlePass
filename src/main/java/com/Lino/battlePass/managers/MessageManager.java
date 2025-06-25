package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {

    private final BattlePass plugin;
    private FileConfiguration messagesConfig;

    public MessageManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String path, Object... replacements) {
        String message = messagesConfig.getString(path, path);

        if (replacements.length > 0 && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i].toString(), replacements[i + 1].toString());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&',
                messagesConfig.getString("prefix", "&6&l[BATTLE PASS] &e"));
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
}