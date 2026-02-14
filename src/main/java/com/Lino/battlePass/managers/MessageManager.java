package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MessageManager {

    private final BattlePass plugin;
    private FileConfiguration messagesConfig;
    private FileConfiguration guiConfig;
    private FileConfiguration legacyMessagesConfig;
    private String language = "ru";

    public MessageManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String configuredLanguage = "RU";
        if (plugin.getConfigManager() != null) {
            configuredLanguage = plugin.getConfigManager().getLanguage();
        } else {
            configuredLanguage = plugin.getConfig().getString("language", "RU");
        }

        language = normalizeLanguage(configuredLanguage);
        String messagesFileName = "messages_" + language + ".yml";
        String guiFileName = "gui_" + language + ".yml";

        ensureResource(messagesFileName);
        ensureResource(guiFileName);
        ensureResource("messages.yml");

        File messagesFile = new File(plugin.getDataFolder(), messagesFileName);
        File guiFile = new File(plugin.getDataFolder(), guiFileName);
        File legacyMessagesFile = new File(plugin.getDataFolder(), "messages.yml");

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        legacyMessagesConfig = YamlConfiguration.loadConfiguration(legacyMessagesFile);
    }

    public String getMessage(String path, Object... replacements) {
        String message = readString(messagesConfig, path);
        if (message == null) {
            message = readString(guiConfig, path);
        }
        if (message == null) {
            message = readString(legacyMessagesConfig, path);
        }
        if (message == null) {
            message = path;
        }

        return GradientColorParser.parse(applyReplacements(message, replacements));
    }

    public String getGuiMessage(String path, Object... replacements) {
        String message = readString(guiConfig, path);
        if (message == null) {
            message = readString(messagesConfig, path);
        }
        if (message == null) {
            message = readString(legacyMessagesConfig, path);
        }
        if (message == null) {
            message = path;
        }

        return GradientColorParser.parse(applyReplacements(message, replacements));
    }

    public String getGuiMessageStripped(String path, Object... replacements) {
        return ChatColor.stripColor(getGuiMessage(path, replacements));
    }

    public List<String> getMessages(String path, Object... replacements) {
        List<String> lines = readStringList(messagesConfig, path);
        if (lines.isEmpty()) {
            lines = readStringList(legacyMessagesConfig, path);
        }
        return processLines(lines, replacements);
    }

    public List<String> getGuiMessages(String path, Object... replacements) {
        List<String> lines = readStringList(guiConfig, path);
        if (lines.isEmpty()) {
            lines = readStringList(messagesConfig, path);
        }
        if (lines.isEmpty()) {
            lines = readStringList(legacyMessagesConfig, path);
        }
        return processLines(lines, replacements);
    }

    public String getPrefix() {
        String prefix = readString(messagesConfig, "prefix");
        if (prefix == null) {
            prefix = readString(legacyMessagesConfig, "prefix");
        }
        if (prefix == null) {
            prefix = "&6[BattlePass] &8» &r";
        }
        return GradientColorParser.parse(prefix);
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig != null) {
            return messagesConfig;
        }
        return legacyMessagesConfig;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public String getLanguage() {
        return language.toUpperCase(Locale.ROOT);
    }

    private String normalizeLanguage(String value) {
        if (value == null) {
            return "ru";
        }

        String normalized = value.trim();
        if ("EN".equalsIgnoreCase(normalized) ||
                "ENG".equalsIgnoreCase(normalized) ||
                "ENGLISH".equalsIgnoreCase(normalized)) {
            return "en";
        }
        return "ru";
    }

    private void ensureResource(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (file.exists()) {
            return;
        }

        try {
            plugin.saveResource(fileName, false);
        } catch (IllegalArgumentException ignored) {
            // Optional language file may be absent in legacy forks.
        }
    }

    private String readString(FileConfiguration config, String path) {
        if (config == null) {
            return null;
        }
        return config.getString(path);
    }

    private List<String> readStringList(FileConfiguration config, String path) {
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> list = config.getStringList(path);
        return list == null ? Collections.emptyList() : list;
    }

    private String applyReplacements(String value, Object... replacements) {
        String result = value;
        if (replacements.length > 0 && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                String target = replacements[i] != null ? replacements[i].toString() : "null";
                String replacement = replacements[i + 1] != null ? replacements[i + 1].toString() : "null";
                result = result.replace(target, replacement);
            }
        }
        return result;
    }

    private List<String> processLines(List<String> lines, Object... replacements) {
        List<String> processed = new ArrayList<>(lines.size());
        for (String line : lines) {
            String replaced = applyReplacements(line, replacements);
            processed.add(GradientColorParser.parse(replaced));
        }
        return processed;
    }
}
