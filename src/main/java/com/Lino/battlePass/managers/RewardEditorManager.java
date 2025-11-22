package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.LevelRewardEditGui;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.GradientColorParser;
import com.Lino.battlePass.utils.ItemSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RewardEditorManager {

    private final BattlePass plugin;
    private final Map<UUID, LevelRewardEditGui> activeEditors;
    private final Map<UUID, CommandInputState> commandInputStates;

    public RewardEditorManager(BattlePass plugin) {
        this.plugin = plugin;
        this.activeEditors = new ConcurrentHashMap<>();
        this.commandInputStates = new ConcurrentHashMap<>();
    }

    public void openLevelEditor(Player player, int level, boolean isPremium) {
        LevelRewardEditGui editor = new LevelRewardEditGui(plugin, player, level, isPremium);
        activeEditors.put(player.getUniqueId(), editor);
        editor.open();
    }

    public LevelRewardEditGui getActiveEditor(UUID uuid) {
        return activeEditors.get(uuid);
    }

    public void removeActiveEditor(UUID uuid) {
        activeEditors.remove(uuid);
    }

    public void startCommandInput(Player player, int level, boolean isPremium) {
        commandInputStates.put(player.getUniqueId(), new CommandInputState(level, isPremium));

        player.closeInventory();
        player.sendMessage(GradientColorParser.parse(
                plugin.getMessageManager().getPrefix() +
                        "<gradient:#FFD700:#FF6B6B>Enter command reward format:</gradient>"));
        player.sendMessage(GradientColorParser.parse(
                "&7Example: &e$1000 | eco give <player> 1000"));
        player.sendMessage(GradientColorParser.parse(
                "&7Type &c'cancel' &7to cancel"));
    }

    public boolean handleCommandInput(Player player, String message) {
        CommandInputState state = commandInputStates.get(player.getUniqueId());
        if (state == null) {
            return false;
        }

        commandInputStates.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(GradientColorParser.parse(
                    plugin.getMessageManager().getPrefix() +
                            "<gradient:#FF6B6B:#FF0000>Command input cancelled</gradient>"));
            openLevelEditor(player, state.level, state.isPremium);
            return true;
        }

        String[] parts = message.split("\\|", 2);
        if (parts.length != 2) {
            player.sendMessage(GradientColorParser.parse(
                    plugin.getMessageManager().getPrefix() +
                            "<gradient:#FF6B6B:#FF0000>Invalid format! Use: display | command</gradient>"));
            openLevelEditor(player, state.level, state.isPremium);
            return true;
        }

        String display = parts[0].trim();
        String command = parts[1].trim();

        LevelRewardEditGui editor = getActiveEditor(player.getUniqueId());
        if (editor == null) {
            editor = new LevelRewardEditGui(plugin, player, state.level, state.isPremium);
            activeEditors.put(player.getUniqueId(), editor);
        }

        editor.addReward(new EditableReward(command, display));

        player.sendMessage(GradientColorParser.parse(
                plugin.getMessageManager().getPrefix() +
                        "<gradient:#00FF88:#45B7D1>✓ Command reward added!</gradient>"));

        final LevelRewardEditGui finalEditor = editor;
        new BukkitRunnable() {
            @Override
            public void run() {
                finalEditor.open();
            }
        }.runTaskLater(plugin, 20L);

        return true;
    }

    public void saveRewards(Player player, int level, boolean isPremium, List<EditableReward> rewards) {
        String fileName = isPremium ? "BattlePassPREMIUM.yml" : "BattlePassFREE.yml";
        File file = new File(plugin.getDataFolder(), fileName);
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String levelPath = "level-" + level;

        config.set(levelPath, null);

        if (rewards.isEmpty()) {
            saveConfig(file, config);
            plugin.reload(); // RELOAD AUTOMATICO
            player.sendMessage(GradientColorParser.parse(
                    plugin.getMessageManager().getPrefix() +
                            "<gradient:#00FF88:#45B7D1>✓ Level " + level + " rewards cleared!</gradient>"));
            return;
        }

        if (rewards.size() == 1) {
            EditableReward reward = rewards.get(0);
            if (reward.isCommand()) {
                config.set(levelPath + ".command", reward.getCommand());
                config.set(levelPath + ".display", reward.getDisplayName());
            } else {
                ItemSerializer.saveItemToConfig(config.createSection(levelPath), reward.getItemStack());
            }
        } else {
            ConfigurationSection itemsSection = config.createSection(levelPath + ".items");
            int index = 1;

            for (EditableReward reward : rewards) {
                String itemKey = "reward" + index;

                if (reward.isCommand()) {
                    itemsSection.set(itemKey + ".command", reward.getCommand());
                    itemsSection.set(itemKey + ".display", reward.getDisplayName());
                } else {
                    ItemSerializer.saveItemToConfig(
                            itemsSection.createSection(itemKey),
                            reward.getItemStack()
                    );
                }
                index++;
            }
        }

        saveConfig(file, config);
        plugin.reload(); // RELOAD AUTOMATICO

        player.sendMessage(GradientColorParser.parse(
                plugin.getMessageManager().getPrefix() +
                        "<gradient:#00FF88:#45B7D1>✓ Level " + level + " rewards saved!</gradient>"));
    }

    public void saveAllRewards(Player player) {
        plugin.reload();

        player.sendMessage(GradientColorParser.parse(
                plugin.getMessageManager().getPrefix() +
                        "<gradient:#00FF88:#45B7D1>✓ All rewards saved and plugin reloaded!</gradient>"));

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.sendMessage(GradientColorParser.parse(
                        plugin.getMessageManager().getPrefix() +
                                "<gradient:#FFD700:#FF6B6B>Battle Pass rewards have been updated by an admin!</gradient>"));
            }
        }
    }

    private void saveConfig(File file, FileConfiguration config) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save rewards configuration: " + e.getMessage());
        }
    }

    public boolean hasCommandInput(UUID uuid) {
        return commandInputStates.containsKey(uuid);
    }

    private static class CommandInputState {
        final int level;
        final boolean isPremium;

        CommandInputState(int level, boolean isPremium) {
            this.level = level;
            this.isPremium = isPremium;
        }
    }
}