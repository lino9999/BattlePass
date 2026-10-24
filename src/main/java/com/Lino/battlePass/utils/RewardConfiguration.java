package com.Lino.battlePass.utils;

import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.models.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Shared parsing; interactive editors reject unreadable entries instead of silently deleting them on save. */
public final class RewardConfiguration {
    private static final Pattern LEVEL_KEY = Pattern.compile("level-[1-9]\\d*");
    private RewardConfiguration() {}

    /** Shared active/archive definitions; empty configured levels remain available to editor pagination. */
    public static Map<Integer, List<Reward>> readRuntimeRewards(FileConfiguration config, boolean free, Consumer<String> warning) {
        Map<Integer, List<Reward>> levels = new TreeMap<>();
        for (String key : config.getKeys(false)) {
            if (!LEVEL_KEY.matcher(key).matches()) continue;
            int level;
            try { level = Integer.parseInt(key.substring("level-".length())); }
            catch (NumberFormatException ignored) { continue; }
            List<Reward> rewards = new ArrayList<>();
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                warning.accept("Invalid reward level at " + key + ". Skipping.");
            } else if (section.contains("command") || section.contains("material") || section.contains("serialized-item")) {
                addRuntime(rewards, section, level, free, warning);
            } else if (section.contains("items")) {
                ConfigurationSection items = section.getConfigurationSection("items");
                if (items == null) warning.accept("Invalid reward items section at " + key + ". Skipping.");
                else for (String itemKey : items.getKeys(false)) {
                    ConfigurationSection item = items.getConfigurationSection(itemKey);
                    if (item == null) warning.accept("Invalid reward entry at " + key + ".items." + itemKey + ". Skipping.");
                    else addRuntime(rewards, item, level, free, warning);
                }
            }
            levels.put(level, rewards);
        }
        return levels;
    }

    private static void addRuntime(List<Reward> rewards, ConfigurationSection section, int level, boolean free, Consumer<String> warning) {
        try {
            if (section.contains("command")) {
                String command = section.getString("command");
                if (command == null || command.isBlank()) throw new IllegalArgumentException("Empty reward command");
                rewards.add(new Reward(level, command, section.getString("display", "Mystery Reward"), free));
            } else {
                ItemStack item = ItemSerializer.loadItemFromConfig(section);
                if (!ItemSerializer.isUsableItem(item)) throw new IllegalArgumentException("Reward item could not be loaded");
                String display = section.getString("display", item.getAmount() + "x " + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
                rewards.add(new Reward(level, item, display, free, section.getCurrentPath()));
            }
        } catch (RuntimeException failure) {
            warning.accept("Invalid " + (free ? "free" : "premium") + " reward at " + section.getCurrentPath() + ": " + failure.getMessage() + ". Skipping.");
        }
    }

    public static List<EditableReward> readEditableRewards(FileConfiguration config, int level) {
        return read(config, level, false);
    }

    public static List<EditableReward> readEditableRewardsStrict(FileConfiguration config, int level) {
        return read(config, level, true);
    }

    private static List<EditableReward> read(FileConfiguration config, int level, boolean strict) {
        List<EditableReward> rewards = new ArrayList<>();
        String path = "level-" + level;
        if (!config.contains(path)) return rewards;
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            if (strict) throw new IllegalArgumentException("Invalid reward level: " + path);
            return rewards;
        }
        if (section.contains("command") || section.contains("material") || section.contains("serialized-item")) {
            add(rewards, section, strict);
        } else if (section.contains("items")) {
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items == null) {
                if (strict) throw new IllegalArgumentException("Invalid items section: " + path);
                return rewards;
            }
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    if (strict) throw new IllegalArgumentException("Invalid reward entry: " + path + ".items." + key);
                    continue;
                }
                add(rewards, item, strict);
            }
        } else if (strict && !section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("Unrecognized reward definition: " + path);
        }
        return rewards;
    }

    private static void add(List<EditableReward> rewards, ConfigurationSection section, boolean strict) {
        if (section.contains("command")) {
            String command = section.getString("command");
            if (strict && (command == null || command.isBlank())) throw new IllegalArgumentException("Reward command is empty");
            rewards.add(new EditableReward(command, section.getString("display", "Mystery Reward")));
        } else {
            ItemStack item = strict ? ItemSerializer.loadItemFromConfigStrict(section) : ItemSerializer.loadItemFromConfig(section);
            if (item != null) rewards.add(EditableReward.itemWithDisplay(item, section.getString("display")));
            else if (strict) throw new IllegalArgumentException("Reward item could not be loaded: " + section.getCurrentPath());
        }
    }
}
