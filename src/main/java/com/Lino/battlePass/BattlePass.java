package com.Lino.battlePass;

import org.bukkit.plugin.java.JavaPlugin;
import com.Lino.battlePass.managers.*;
import com.Lino.battlePass.commands.BattlePassCommand;
import com.Lino.battlePass.listeners.EventManager;
import com.Lino.battlePass.tasks.BattlePassTask;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;

public class BattlePass extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private MissionManager missionManager;
    private RewardManager rewardManager;
    private GuiManager guiManager;
    private MessageManager messageManager;
    private EventManager eventManager;
    private ShopManager shopManager;
    private CoinsDistributionTask coinsDistributionTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("missions.yml", false);
        saveResource("messages.yml", false);
        saveResource("BattlePassFREE.yml", false);
        saveResource("BattlePassPREMIUM.yml", false);
        saveResource("shop.yml", false);

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        databaseManager = new DatabaseManager(this);
        playerDataManager = new PlayerDataManager(this, databaseManager);
        rewardManager = new RewardManager(this, configManager);
        missionManager = new MissionManager(this, configManager, databaseManager, playerDataManager);
        shopManager = new ShopManager(this);
        guiManager = new GuiManager(this, playerDataManager, missionManager, rewardManager, messageManager, configManager);

        databaseManager.initialize().thenRun(() -> {
            getServer().getScheduler().runTask(this, () -> {
                rewardManager.loadRewards();
                missionManager.initialize();

                new BukkitRunnable() {
                    private int attempts = 0;
                    private static final int MAX_ATTEMPTS = 60;

                    @Override
                    public void run() {
                        attempts++;

                        if (missionManager.isInitialized()) {
                            playerDataManager.loadOnlinePlayers();

                            eventManager = new EventManager(BattlePass.this);
                            getServer().getPluginManager().registerEvents(eventManager, BattlePass.this);
                            getCommand("battlepass").setExecutor(new BattlePassCommand(BattlePass.this));

                            new BattlePassTask(BattlePass.this).runTaskTimer(BattlePass.this, 6000L, 1200L);

                            databaseManager.loadCoinsDistributionTime().thenAccept(nextDist -> {
                                coinsDistributionTask = new CoinsDistributionTask(BattlePass.this);
                                if (nextDist != null) {
                                    coinsDistributionTask.setNextDistribution(nextDist);
                                }
                                coinsDistributionTask.runTaskTimer(BattlePass.this, 200L, 1200L);
                            });

                            getLogger().info(messageManager.getMessage("messages.plugin-enabled"));
                            this.cancel();
                        } else if (attempts >= MAX_ATTEMPTS) {
                            getLogger().severe("Failed to initialize MissionManager after 30 seconds!");
                            this.cancel();
                        }
                    }
                }.runTaskTimer(this, 0L, 10L);
            });
        });
    }

    @Override
    public void onDisable() {
        if (coinsDistributionTask != null) {
            coinsDistributionTask.cancel();
        }
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
        shopManager.reload();
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

    public ShopManager getShopManager() {
        return shopManager;
    }

    public CoinsDistributionTask getCoinsDistributionTask() {
        return coinsDistributionTask;
    }
}