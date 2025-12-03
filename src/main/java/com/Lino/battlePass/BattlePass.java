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

import java.io.BufferedReader;
import java.io.File;
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
    private CustomItemManager customItemManager;
    private SoundManager soundManager;
    private RewardEditorManager rewardEditorManager;
    private BattlePassExpansion placeholderExpansion;
    private MissionEditorManager missionEditorManager;

    private boolean updateAvailable = false;
    private String latestVersion = "";
    private static final String SPIGOT_RESOURCE_ID = "125992";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfNotExists("missions.yml");
        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("BattlePassFREE.yml");
        saveResourceIfNotExists("BattlePassPREMIUM.yml");
        saveResourceIfNotExists("shop.yml");

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
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
        rewardEditorManager = new RewardEditorManager(this);

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

                            registerPlaceholders();
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

    private void saveResourceIfNotExists(String filename) {
        File file = new File(getDataFolder(), filename);
        if (!file.exists()) {
            saveResource(filename, false);
        }
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (soundManager != null) {
            soundManager.stopAllSounds();
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

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new BattlePassExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI support enabled!");
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
        missionManager.recalculateResetTimeOnReload();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() != null && player.getOpenInventory().getTitle() != null) {
                String title = player.getOpenInventory().getTitle();
                boolean isBattlePassGUI = false;
                int currentPage = 1;

                for (int i = 1; i <= 6; i++) {
                    if (title.equals(messageManager.getMessage("gui.battlepass", "%page%", String.valueOf(i)))) {
                        isBattlePassGUI = true;
                        currentPage = i;
                        break;
                    }
                }

                if (isBattlePassGUI) {
                    player.closeInventory();
                    final int page = currentPage;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        guiManager.openBattlePassGUI(player, page);
                    }, 1L);
                } else if (title.equals(messageManager.getMessage("gui.leaderboard"))) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        guiManager.openLeaderboardGUI(player);
                    }, 1L);
                } else if (title.equals(messageManager.getMessage("gui.missions"))) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        guiManager.openMissionsGUI(player);
                    }, 1L);
                } else if (title.equals(messageManager.getMessage("gui.shop"))) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        guiManager.openShopGUI(player);
                    }, 1L);
                }
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
}