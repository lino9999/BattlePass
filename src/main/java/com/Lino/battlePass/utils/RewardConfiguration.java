package com.Lino.battlePass.utils;

import com.Lino.battlePass.models.EditableReward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Shared parsing; interactive editors reject unreadable entries instead of silently deleting them on save. */
public final class RewardConfiguration {
    private RewardConfiguration() {}

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
