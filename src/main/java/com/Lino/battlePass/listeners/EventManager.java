package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

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
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerConnectionListener.initializePlayTime(player);
            missionProgressListener.initializePlayerLocation(player.getUniqueId(), player.getLocation());
        }
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(playerConnectionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(missionProgressListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(customItemsListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guiClickListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(rewardsEditorListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(missionEditorListener, plugin);

        registerMythicMobs(plugin.getServer().getPluginManager());
    }

    private void registerMythicMobs(PluginManager plugins) {
        if (!plugins.isPluginEnabled("MythicMobs")) return;
        Listener optional = null;
        try {
            optional = new MythicMobsListener(plugin);
            plugins.registerEvents(optional, plugin);
            plugin.getLogger().info("MythicMobs found! Hooking into MythicMobs events...");
        } catch (RuntimeException | LinkageError failure) {
            if (optional != null) {
                try { HandlerList.unregisterAll(optional); }
                catch (RuntimeException | LinkageError cleanupFailure) {
                    plugin.getLogger().warning("Could not remove failed MythicMobs listener: " + cleanupFailure.getMessage());
                }
            }
            plugin.getLogger().warning("MythicMobs integration unavailable; continuing without custom-mob missions: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public PlayerConnectionListener getPlayerConnectionListener() {
        return playerConnectionListener;
    }

    public void flushPlayTime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerConnectionListener.updatePlayTime(player);
        }
    }

    public void stopCustomItemEffects() { customItemsListener.shutdown(); }
    public void stopJoinFeedback() { playerConnectionListener.stopJoinFeedback(); }

    public Object beginPlayTimeCycle() {
        flushPlayTime();
        return playerConnectionListener.beginPlayTimeCycle();
    }

    public void commitPlayTimeCycle(Object token) {
        flushPlayTime();
        playerConnectionListener.commitPlayTimeCycle(token);
    }

    public void abortPlayTimeCycle(Object token) { playerConnectionListener.abortPlayTimeCycle(token); }

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
