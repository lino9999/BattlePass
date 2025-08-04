package com.Lino.battlePass;

import com.Lino.battlePass.commands.BattlePassTabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.Lino.battlePass.managers.*;
import com.Lino.battlePass.commands.BattlePassCommand;
import com.Lino.battlePass.listeners.EventManager;
import com.Lino.battlePass.tasks.BattlePassTask;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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

    private boolean updateAvailable = false;
    private String latestVersion = "";
    private static final String SPIGOT_RESOURCE_ID = "125992";

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
                            getCommand("battlepass").setTabCompleter(new BattlePassTabCompleter());

                            new BattlePassTask(BattlePass.this).runTaskTimer(BattlePass.this, 6000L, 1200L);

                            databaseManager.loadCoinsDistributionTime().thenAccept(nextDist -> {
                                coinsDistributionTask = new CoinsDistributionTask(BattlePass.this);
                                if (nextDist != null) {
                                    coinsDistributionTask.setNextDistribution(nextDist);
                                }
                                coinsDistributionTask.runTaskTimer(BattlePass.this, 200L, 1200L);
                            });

                            checkForUpdates();

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

    private void checkForUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URI uri = new URI("https://api.spigotmc.org/legacy/update.php?resource=125992");
                    URL url = uri.toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("User-Agent", "BattlePass-UpdateChecker");

                    if (connection.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String version = reader.readLine();
                        reader.close();

                        if (version != null && !version.trim().isEmpty()) {
                            String currentVersion = getDescription().getVersion();

                            if (!version.equals(currentVersion)) {
                                updateAvailable = true;
                                latestVersion = version;

                                Bukkit.getScheduler().runTask(BattlePass.this, () -> {
                                    getLogger().warning("=====================================");
                                    getLogger().warning("  A new version is available!");
                                    getLogger().warning("  Current version: " + currentVersion);
                                    getLogger().warning("  Latest version: " + version);
                                    getLogger().warning("  Download at: https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID);
                                    getLogger().warning("=====================================");
                                });
                            } else {
                                Bukkit.getScheduler().runTask(BattlePass.this, () -> {
                                    getLogger().info("You are running the latest version!");
                                });
                            }
                        }
                    } else {
                        getLogger().info("Could not check for updates: Response code " + connection.getResponseCode());
                    }

                    connection.disconnect();
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(BattlePass.this, () -> {
                        getLogger().info("Could not check for updates: " + e.getMessage());
                    });
                }
            }
        }.runTaskAsynchronously(this);
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
        rewardManager.loadRewards();
        shopManager.reload();
        guiManager.clearCache();
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

    public ShopManager getShopManager() {
        return shopManager;
    }

    public CoinsDistributionTask getCoinsDistributionTask() {
        return coinsDistributionTask;
    }

    public void setCoinsDistributionTask(CoinsDistributionTask task) {
        this.coinsDistributionTask = task;
    }
}