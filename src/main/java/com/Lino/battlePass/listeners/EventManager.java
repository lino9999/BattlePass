package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.NamespacedKey;

public class EventManager {

    private final BattlePass plugin;
    private final NamespacedKey navigationKey;

    private final PlayerConnectionListener playerConnectionListener;
    private final MissionProgressListener missionProgressListener;
    private final CustomItemsListener customItemsListener;
    private final GuiClickListener guiClickListener;
    private final RewardsEditorListener rewardsEditorListener;
    private final MissionEditorListener missionEditorListener;

    public EventManager(BattlePass plugin) {
        this.plugin = plugin;
        this.navigationKey = new NamespacedKey(plugin, "navigation");

        this.playerConnectionListener = new PlayerConnectionListener(plugin);
        this.missionProgressListener = new MissionProgressListener(plugin);
        this.customItemsListener = new CustomItemsListener(plugin);
        this.guiClickListener = new GuiClickListener(plugin);
        this.rewardsEditorListener = new RewardsEditorListener(plugin);
        this.missionEditorListener = new MissionEditorListener(plugin);

        registerListeners();
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(playerConnectionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(missionProgressListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(customItemsListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guiClickListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(rewardsEditorListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(missionEditorListener, plugin);

        // Check for MythicMobs hook
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            plugin.getLogger().info("MythicMobs found! Hooking into MythicMobs events...");
            plugin.getServer().getPluginManager().registerEvents(new MythicMobsListener(plugin), plugin);
        }
    }

    public PlayerConnectionListener getPlayerConnectionListener() {
        return playerConnectionListener;
    }

    public MissionProgressListener getMissionProgressListener() {
        return missionProgressListener;
    }

    public CustomItemsListener getCustomItemsListener() {
        return customItemsListener;
    }

    public GuiClickListener getGuiClickListener() {
        return guiClickListener;
    }

    public NamespacedKey getNavigationKey() {
        return navigationKey;
    }
}