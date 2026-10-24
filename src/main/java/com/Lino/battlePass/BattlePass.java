package com.Lino.battlePass;

import com.Lino.battlePass.commands.BattlePassTabCompleter;
import com.Lino.battlePass.listeners.MissionProgressListener;
import com.Lino.battlePass.placeholders.BattlePassExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.Lino.battlePass.managers.*;
import com.Lino.battlePass.commands.BattlePassCommand;
import com.Lino.battlePass.listeners.EventManager;
import com.Lino.battlePass.tasks.BattlePassTask;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
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
    private CustomItemManager customItemManager;
    private SoundManager soundManager;
    private RewardEditorManager rewardEditorManager;
    private BattlePassExpansion placeholderExpansion;
    private MissionEditorManager missionEditorManager;
    private SeasonRotationManager seasonRotationManager;
    private XPEventManager xpEventManager;

    private volatile boolean stopping;
    private volatile long lifecycleGeneration;
    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = "";

    @Override
    public void onEnable() {
        stopping = false;
        long generation = ++lifecycleGeneration;
        updateAvailable = false;
        latestVersion = "";
        saveDefaultConfig();
        saveResourceIfNotExists("missions.yml");
        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("BattlePassFREE.yml");
        saveResourceIfNotExists("BattlePassPREMIUM.yml");
        saveResourceIfNotExists("shop.yml");

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        seasonRotationManager = new SeasonRotationManager(this);

        configManager.reload();

        xpEventManager = new XPEventManager(this);

        databaseManager = new DatabaseManager(this);
        playerDataManager = new PlayerDataManager(this, databaseManager);
        rewardManager = new RewardManager(this, configManager);
        missionManager = new MissionManager(this, configManager, databaseManager, playerDataManager);
        shopManager = new ShopManager(this);
        customItemManager = new CustomItemManager(this);
        soundManager = new SoundManager(this, customItemManager);
        guiManager = new GuiManager(this, playerDataManager, missionManager, rewardManager, messageManager, configManager);
        rewardEditorManager = new RewardEditorManager(this);
        missionEditorManager = new MissionEditorManager(this);

        databaseManager.initialize().thenRun(() -> {
            if (!isLifecycleActive(generation)) return;
            getServer().getScheduler().runTask(this, () -> {
                if (!isLifecycleActive(generation)) return;
                try {
                    rewardManager.loadRewards();
                    missionManager.initialize();
                    waitForMissions(generation);
                } catch (RuntimeException failure) { failStartup(generation, failure); }
            });
        }).exceptionally(ex -> {
            failStartup(generation, ex);
            return null;
        });
    }

    private void waitForMissions(long generation) {
        new BukkitRunnable() {
            private int attempts;
            private static final int MAX_ATTEMPTS = 60;

            @Override
            public void run() {
                if (!isLifecycleActive(generation)) { cancel(); return; }
                try {
                    attempts++;
                    if (missionManager.isInitialized()) {
                        completeStartup();
                        cancel();
                    } else if (attempts >= MAX_ATTEMPTS) {
                        getLogger().severe("Failed to initialize MissionManager after 30 seconds!");
                        cancel();
                        getServer().getPluginManager().disablePlugin(BattlePass.this);
                    }
                } catch (RuntimeException failure) {
                    cancel();
                    failStartup(generation, failure);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void completeStartup() {
        playerDataManager.loadOnlinePlayers();
        eventManager = new EventManager(this);
        getCommand("battlepass").setExecutor(new BattlePassCommand(this));
        getCommand("battlepass").setTabCompleter(new BattlePassTabCompleter());
        new BattlePassTask(this).runTaskTimer(this, 6000L, 1200L);
        restoreCoinsDistributionTask();
        registerPlaceholders();
        checkForUpdates();
        getLogger().info("✓ Battle Pass enabled successfully!");
    }

    private boolean isLifecycleActive(long generation) {
        return !stopping && lifecycleGeneration == generation && isEnabled();
    }

    private void failStartup(long generation, Throwable failure) {
        if (!isLifecycleActive(generation)) return;
        getLogger().severe("BattlePass could not start: " + failure.getMessage());
        getServer().getScheduler().runTask(this, () -> {
            if (isLifecycleActive(generation)) getServer().getPluginManager().disablePlugin(this);
        });
    }

    public boolean isStopping() { return stopping; }

    private void saveResourceIfNotExists(String filename) {
        File file = new File(getDataFolder(), filename);
        if (!file.exists()) {
            saveResource(filename, false);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        lifecycleGeneration++;
        if (seasonRotationManager != null) seasonRotationManager.shutdown();
        if (eventManager != null) {
            eventManager.stopCustomItemEffects();
            eventManager.stopJoinFeedback();
            eventManager.flushPlayTime();
        }
        if (xpEventManager != null) {
            xpEventManager.shutdown();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.shutdown();
            placeholderExpansion.unregister();
        }
        if (soundManager != null) {
            soundManager.shutdown();
        }
        if (coinsDistributionTask != null) {
            coinsDistributionTask.cancel();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayersSync();
        }
        if (missionManager != null) {
            missionManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    private void restoreCoinsDistributionTask() {
        long generation = lifecycleGeneration;
        databaseManager.loadCoinsDistributionTime()
                .thenAccept(deadline -> {
                    if (isLifecycleActive(generation)) startCoinsDistributionTask(deadline);
                })
                .exceptionally(ex -> {
                    if (!isLifecycleActive(generation)) return null;
                    getLogger().severe("Could not load coins distribution time: " + ex.getMessage());
                    getServer().getScheduler().runTask(BattlePass.this, () -> {
                        if (isLifecycleActive(generation)) getServer().getPluginManager().disablePlugin(BattlePass.this);
                    });
                    return null;
                });
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new BattlePassExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI support enabled!");
        }
    }

    private void checkForUpdates() {
        long generation = lifecycleGeneration;
        if (!isLifecycleActive(generation)) return;
        String installed = getDescription().getVersion();
        try {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!isLifecycleActive(generation)) return;
                    try {
                        UpdateChecker.Result result = UpdateChecker.check(installed);
                        if (result == null) return;
                        publishUpdateResult(generation, () -> {
                            latestVersion = result.newer() ? result.version() : "";
                            updateAvailable = result.newer();
                            if (result.newer()) {
                                getLogger().warning("=====================================");
                                getLogger().warning("  A new version is available!");
                                getLogger().warning("  Current version: " + installed);
                                getLogger().warning("  Latest version: " + result.version());
                                getLogger().warning("  Download at: https://www.spigotmc.org/resources/" + UpdateChecker.RESOURCE_ID);
                                getLogger().warning("=====================================");
                            } else {
                                getLogger().info("You are running the latest version!");
                            }
                        });
                    } catch (Exception failure) {
                        publishUpdateResult(generation, () -> getLogger().info("Could not check for updates: " + failure.getMessage()));
                    }
                }
            }.runTaskAsynchronously(this);
        } catch (RuntimeException rejected) {
            if (isLifecycleActive(generation)) getLogger().info("Could not start update check: " + rejected.getMessage());
        }
    }

    private void publishUpdateResult(long generation, Runnable publication) {
        if (!isLifecycleActive(generation)) return;
        try {
            getServer().getScheduler().runTask(this, () -> {
                if (isLifecycleActive(generation)) publication.run();
            });
        } catch (RuntimeException rejected) {
            if (isLifecycleActive(generation)) getLogger().info("Could not report update check: " + rejected.getMessage());
        }
    }

    public void reload() {
        reloadConfig();
        seasonRotationManager.reload();
        configManager.reload();
        messageManager.reload();
        rewardManager.loadRewards();
        shopManager.reload();
        // Page history is navigation state; the owned menus below are rebuilt from fresh configuration.
        missionManager.recalculateResetTimeOnReload();
        if (!stopping && missionManager.isInitialized()) seasonRotationManager.createDefaultSeasonFolders();

        for (Player player : Bukkit.getOnlinePlayers()) {
            var view = player.getOpenInventory();
            var context = com.Lino.battlePass.gui.BaseHolder.contextOf(view.getTopInventory());
            if (context == null) continue;
            switch (context.type()) {
                case BATTLE_PASS, LEADERBOARD, MISSIONS, SHOP -> {
                    player.closeInventory();
                    var closedTo = player.getOpenInventory().getTopInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (stopping || !player.isOnline() || player.getOpenInventory().getTopInventory() != closedTo) return;
                        switch (context.type()) {
                            case BATTLE_PASS -> guiManager.openBattlePassGUI(player, context.page());
                            case LEADERBOARD -> guiManager.openLeaderboardGUI(player);
                            case MISSIONS -> guiManager.openMissionsGUI(player);
                            case SHOP -> guiManager.openShopGUI(player);
                            default -> { }
                        }
                    }, 1L);
                }
                default -> { }
            }
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
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

    public MissionEditorManager getMissionEditorManager() {
        return missionEditorManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public CoinsDistributionTask getCoinsDistributionTask() {
        return coinsDistributionTask;
    }

    public void setCoinsDistributionTask(CoinsDistributionTask task) {
        this.coinsDistributionTask = task;
    }

    public void startCoinsDistributionTask(LocalDateTime savedTime) {
        if (stopping) return;
        if (!Bukkit.isPrimaryThread()) {
            long generation = lifecycleGeneration;
            Bukkit.getScheduler().runTask(this, () -> {
                if (lifecycleGeneration == generation) startCoinsDistributionTask(savedTime);
            });
            return;
        }

        LocalDateTime nextTime = savedTime;
        if (coinsDistributionTask != null) {
            if (nextTime == null) nextTime = coinsDistributionTask.getNextDistribution();
            coinsDistributionTask.cancel();
        }

        CoinsDistributionTask task = new CoinsDistributionTask(this);
        if (nextTime != null) {
            task.setNextDistribution(nextTime);
        } else {
            task.resetDistributionTime();
        }

        coinsDistributionTask = task;
        task.runTaskTimer(this, 200L, 1200L);
    }

    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public MissionProgressListener getMissionProgressListener() {
        if (eventManager != null) {
            return eventManager.getMissionProgressListener();
        }
        return null;
    }

    public RewardEditorManager getRewardEditorManager() {
        return rewardEditorManager;
    }

    public SeasonRotationManager getSeasonRotationManager() {
        return seasonRotationManager;
    }

    public XPEventManager getXpEventManager() {
        return xpEventManager;
    }
}
