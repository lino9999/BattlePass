package com.Lino.battlePass;

import org.bukkit.plugin.java.JavaPlugin;
import com.Lino.battlePass.managers.*;
import com.Lino.battlePass.commands.BattlePassCommand;
import com.Lino.battlePass.listeners.EventManager;
import com.Lino.battlePass.tasks.BattlePassTask;
import org.bukkit.scheduler.BukkitRunnable;

public class BattlePass extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private MissionManager missionManager;
    private RewardManager rewardManager;
    private GuiManager guiManager;
    private MessageManager messageManager;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("missions.yml", false);
        saveResource("messages.yml", false);

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        databaseManager = new DatabaseManager(this);
        playerDataManager = new PlayerDataManager(this, databaseManager);
        rewardManager = new RewardManager(this, configManager);
        missionManager = new MissionManager(this, configManager, databaseManager, playerDataManager);
        guiManager = new GuiManager(this, playerDataManager, missionManager, rewardManager, messageManager, configManager);

        databaseManager.initialize().thenRun(() -> {
            getServer().getScheduler().runTask(this, () -> {
                rewardManager.loadRewards();
                missionManager.initialize();
                playerDataManager.loadOnlinePlayers();

                eventManager = new EventManager(this);
                getServer().getPluginManager().registerEvents(eventManager, this);
                getCommand("battlepass").setExecutor(new BattlePassCommand(this));

                new BattlePassTask(this).runTaskTimer(this, 6000L, 1200L);

                getLogger().info(messageManager.getMessage("messages.plugin-enabled"));
            });
        });
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayers();
        }
        if (missionManager != null) {
            missionManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
        rewardManager.loadRewards();
        guiManager.clearCache();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public MissionManager getMissionManager() {
        return missionManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}