package com.Lino.battlePass;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Statistic;
import org.bukkit.Location;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BattlePass extends JavaPlugin implements Listener, CommandExecutor {

    private Connection connection;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSaves = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> currentPages = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playTimeStart = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();

    private volatile List<Mission> dailyMissions = new ArrayList<>();
    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private final Map<Integer, List<Reward>> freeRewardsByLevel = new HashMap<>();
    private final Map<Integer, List<Reward>> premiumRewardsByLevel = new HashMap<>();

    private FileConfiguration config;
    private FileConfiguration missionsConfig;
    private FileConfiguration messagesConfig;
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;
    private int seasonDuration = 30;
    private int xpPerLevel = 200;
    private int dailyMissionsCount = 7;

    private static final long SAVE_DELAY = 5000L;
    private static final long BATCH_SAVE_INTERVAL = 30000L;
    private final Set<Material> oreTypes = EnumSet.noneOf(Material.class);

    private PreparedStatement insertPlayerStmt;
    private PreparedStatement updatePlayerStmt;
    private PreparedStatement selectPlayerStmt;
    private PreparedStatement insertMissionStmt;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("missions.yml", false);
        saveResource("messages.yml", false);

        loadConfigurations();
        initializeOreTypes();

        getServer().getPluginManager().registerEvents(this, this);

        CompletableFuture.runAsync(() -> {
            initDatabase();
            loadSeasonData();
            prepareDatabaseStatements();
        }, databaseExecutor).thenRun(() -> {
            Bukkit.getScheduler().runTask(this, () -> {
                loadRewardsFromConfig();
                generateDailyMissions();
                calculateNextReset();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    loadPlayerAsync(player.getUniqueId());
                }
            });
        });

        getCommand("battlepass").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkMissionReset();
                checkSeasonReset();
                checkRewardNotifications();
                updatePlayTime();
            }
        }.runTaskTimer(this, 6000L, 1200L);

        saveScheduler.scheduleAtFixedRate(this::performBatchSave,
                BATCH_SAVE_INTERVAL, BATCH_SAVE_INTERVAL, TimeUnit.MILLISECONDS);

        getLogger().info(getMessage("messages.plugin-enabled"));
    }

    @Override
    public void onDisable() {
        saveScheduler.shutdown();

        performBatchSave();
        saveSeasonData();

        try {
            if (insertPlayerStmt != null) insertPlayerStmt.close();
            if (updatePlayerStmt != null) updatePlayerStmt.close();
            if (selectPlayerStmt != null) selectPlayerStmt.close();
            if (insertMissionStmt != null) insertMissionStmt.close();

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            databaseExecutor.shutdownNow();
        }
    }

    private void initializeOreTypes() {
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
                oreTypes.add(mat);
            }
        }
    }

    private void loadConfigurations() {
        config = getConfig();
        xpPerLevel = config.getInt("experience.xp-per-level", 200);

        File missionsFile = new File(getDataFolder(), "missions.yml");
        missionsConfig = YamlConfiguration.loadConfiguration(missionsFile);
        dailyMissionsCount = missionsConfig.getInt("daily-missions-count", 7);

        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String getMessage(String path, Object... replacements) {
        String message = messagesConfig.getString(path, path);

        if (replacements.length > 0 && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i].toString(), replacements[i + 1].toString());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&',
                messagesConfig.getString("prefix", "&6&l[BATTLE PASS] &e"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("messages.player-only"));
                return true;
            }
            openBattlePassGUI((Player) sender, 1);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelpMessage(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("battlepass.admin")) {
                    sender.sendMessage(getPrefix() + getMessage("messages.no-permission"));
                    return true;
                }
                reloadPlugin(sender);
                return true;

            case "addpremium":
                return handlePremiumCommand(sender, args, true);

            case "removepremium":
                return handlePremiumCommand(sender, args, false);

            case "addxp":
                return handleXPCommand(sender, args, true);

            case "removexp":
                return handleXPCommand(sender, args, false);

            default:
                if (sender instanceof Player) {
                    openBattlePassGUI((Player) sender, 1);
                }
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(getMessage("messages.help.header"));
        sender.sendMessage(getMessage("messages.help.battlepass"));
        sender.sendMessage(getMessage("messages.help.help"));

        if (sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(getMessage("messages.help.reload"));
            sender.sendMessage(getMessage("messages.help.add-premium"));
            sender.sendMessage(getMessage("messages.help.remove-premium"));
            sender.sendMessage(getMessage("messages.help.add-xp"));
            sender.sendMessage(getMessage("messages.help.remove-xp"));
        }
    }

    private void reloadPlugin(CommandSender sender) {
        reloadConfig();
        loadConfigurations();
        itemCache.clear();
        loadRewardsFromConfig();
        generateDailyMissions();
        sender.sendMessage(getPrefix() + getMessage("messages.config-reloaded"));
    }

    private boolean handlePremiumCommand(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(getPrefix() + getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 2) {
            String usage = add ? "messages.usage.add-premium" : "messages.usage.remove-premium";
            sender.sendMessage(getPrefix() + getMessage(usage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getPrefix() + getMessage("messages.player-not-found"));
            return true;
        }

        PlayerData data = getPlayerData(target.getUniqueId());
        data.hasPremium = add;
        markForSave(target.getUniqueId());

        if (add) {
            sender.sendMessage(getPrefix() + getMessage("messages.premium.given-sender",
                    "%target%", target.getName()));
            target.sendMessage(getPrefix() + getMessage("messages.premium.given-target"));
            target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            sender.sendMessage(getPrefix() + getMessage("messages.premium.removed-sender",
                    "%target%", target.getName()));
            target.sendMessage(getPrefix() + getMessage("messages.premium.removed-target"));
        }

        return true;
    }

    private boolean handleXPCommand(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("battlepass.admin")) {
            sender.sendMessage(getPrefix() + getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            String usage = add ? "messages.usage.add-xp" : "messages.usage.remove-xp";
            sender.sendMessage(getPrefix() + getMessage(usage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getPrefix() + getMessage("messages.player-not-found"));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(getPrefix() + getMessage("messages.amount-must-be-positive"));
                return true;
            }

            PlayerData data = getPlayerData(target.getUniqueId());

            if (add) {
                data.xp += amount;
                checkLevelUp(target, data);
                sender.sendMessage(getPrefix() + getMessage("messages.xp.added-sender",
                        "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(getPrefix() + getMessage("messages.xp.added-target",
                        "%amount%", String.valueOf(amount)));
            } else {
                int totalXP = (data.level - 1) * xpPerLevel + data.xp;
                totalXP = Math.max(0, totalXP - amount);

                int newLevel = 1;
                while (totalXP >= xpPerLevel && newLevel < 54) {
                    totalXP -= xpPerLevel;
                    newLevel++;
                }
                data.level = newLevel;
                data.xp = totalXP;

                sender.sendMessage(getPrefix() + getMessage("messages.xp.removed-sender",
                        "%amount%", String.valueOf(amount), "%target%", target.getName()));
                target.sendMessage(getPrefix() + getMessage("messages.xp.removed-target",
                        "%amount%", String.valueOf(amount)));
            }

            markForSave(target.getUniqueId());
            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage(getPrefix() + getMessage("messages.invalid-amount"));
            return true;
        }
    }

    private void initDatabase() {
        try {
            File dbFile = new File(getDataFolder(), "battlepass.db");
            if (!dbFile.exists()) {
                getDataFolder().mkdirs();
            }

            Properties props = new Properties();
            props.setProperty("journal_mode", "WAL");
            props.setProperty("synchronous", "NORMAL");
            props.setProperty("temp_store", "MEMORY");
            props.setProperty("cache_size", "10000");

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), props);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=30000000000");

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS players (" +
                                "uuid TEXT PRIMARY KEY," +
                                "xp INTEGER DEFAULT 0," +
                                "level INTEGER DEFAULT 1," +
                                "claimed_free TEXT DEFAULT ''," +
                                "claimed_premium TEXT DEFAULT ''," +
                                "last_notification INTEGER DEFAULT 0," +
                                "total_levels INTEGER DEFAULT 0," +
                                "has_premium INTEGER DEFAULT 0)"
                );

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS missions (" +
                                "uuid TEXT," +
                                "mission TEXT," +
                                "progress INTEGER DEFAULT 0," +
                                "date TEXT," +
                                "PRIMARY KEY (uuid, mission, date))"
                );

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS season_data (" +
                                "id INTEGER PRIMARY KEY," +
                                "end_date TEXT," +
                                "duration INTEGER)"
                );

                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_missions_uuid_date ON missions(uuid, date)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_players_total_levels ON players(total_levels DESC, level DESC, xp DESC)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void prepareDatabaseStatements() {
        try {
            insertPlayerStmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO players (uuid, xp, level, claimed_free, claimed_premium, " +
                            "last_notification, total_levels, has_premium) VALUES (?, 0, 1, '', '', 0, 0, 0)"
            );

            updatePlayerStmt = connection.prepareStatement(
                    "UPDATE players SET xp = ?, level = ?, claimed_free = ?, claimed_premium = ?, " +
                            "last_notification = ?, total_levels = ?, has_premium = ? WHERE uuid = ?"
            );

            selectPlayerStmt = connection.prepareStatement(
                    "SELECT * FROM players WHERE uuid = ?"
            );

            insertMissionStmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?)"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadSeasonData() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM season_data WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                seasonEndDate = LocalDateTime.parse(rs.getString("end_date"));
                seasonDuration = rs.getInt("duration");

                if (LocalDateTime.now().isAfter(seasonEndDate)) {
                    Bukkit.getScheduler().runTask(this, this::resetSeason);
                }
            } else {
                seasonDuration = config.getInt("season.duration", 30);
                seasonEndDate = LocalDateTime.now().plusDays(seasonDuration);
                saveSeasonData();
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
            seasonDuration = 30;
            seasonEndDate = LocalDateTime.now().plusDays(seasonDuration);
        }
    }

    private void saveSeasonData() {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO season_data (id, end_date, duration) VALUES (1, ?, ?)"
            )) {
                ps.setString(1, seasonEndDate.toString());
                ps.setInt(2, seasonDuration);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);
    }

    private void checkSeasonReset() {
        if (LocalDateTime.now().isAfter(seasonEndDate)) {
            resetSeason();
        }
    }

    private void resetSeason() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(getPrefix() + getMessage("messages.season.reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        CompletableFuture.runAsync(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE players SET xp = 0, level = 1, claimed_free = '', " +
                        "claimed_premium = '', has_premium = 0");
                stmt.executeUpdate("DELETE FROM missions");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, databaseExecutor);

        playerCache.clear();
        pendingSaves.clear();
        seasonEndDate = LocalDateTime.now().plusDays(seasonDuration);
        saveSeasonData();
        generateDailyMissions();
    }

    private String getTimeUntilSeasonEnd() {
        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(now, seasonEndDate);
        long hours = ChronoUnit.HOURS.between(now, seasonEndDate) % 24;

        String dayStr = days == 1 ? getMessage("time.day") : getMessage("time.days");
        String hourStr = hours == 1 ? getMessage("time.hour") : getMessage("time.hours");

        return getMessage("time.days-hours", "%days%", String.valueOf(days),
                "%hours%", String.valueOf(hours));
    }

    private void checkRewardNotifications() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerCache.get(player.getUniqueId());
            if (data == null) continue;

            int availableRewards = countAvailableRewards(player, data);

            if (availableRewards > data.lastNotification) {
                player.sendMessage(getPrefix() + getMessage("messages.rewards-notification",
                        "%amount%", String.valueOf(availableRewards - data.lastNotification)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                data.lastNotification = availableRewards;
                markForSave(player.getUniqueId());
            }
        }
    }

    private int countAvailableRewards(Player player, PlayerData data) {
        int count = 0;

        for (int level : freeRewardsByLevel.keySet()) {
            if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                count++;
            }
        }

        if (data.hasPremium) {
            for (int level : premiumRewardsByLevel.keySet()) {
                if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                    count++;
                }
            }
        }

        return count;
    }

    private CompletableFuture<List<PlayerData>> getTop10PlayersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> allPlayers = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM players ORDER BY total_levels DESC, level DESC, xp DESC LIMIT 10"
            )) {
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
                    data.xp = rs.getInt("xp");
                    data.level = rs.getInt("level");
                    data.totalLevels = rs.getInt("total_levels");
                    allPlayers.add(data);
                }
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return allPlayers;
        }, databaseExecutor);
    }

    private void loadRewardsFromConfig() {
        freeRewards.clear();
        premiumRewards.clear();
        freeRewardsByLevel.clear();
        premiumRewardsByLevel.clear();

        for (int i = 1; i <= 54; i++) {
            String freePath = "rewards.free.level-" + i;
            String premiumPath = "rewards.premium.level-" + i;

            List<Reward> freeLevel = new ArrayList<>();
            List<Reward> premiumLevel = new ArrayList<>();

            if (config.contains(freePath)) {
                if (config.contains(freePath + ".material") || config.contains(freePath + ".command")) {
                    Reward reward = loadSingleReward(freePath, i, true);
                    if (reward != null) {
                        freeRewards.add(reward);
                        freeLevel.add(reward);
                    }
                } else if (config.contains(freePath + ".items")) {
                    for (String key : config.getConfigurationSection(freePath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(freePath + ".items." + key, i, true);
                        if (reward != null) {
                            freeRewards.add(reward);
                            freeLevel.add(reward);
                        }
                    }
                }
            }

            if (config.contains(premiumPath)) {
                if (config.contains(premiumPath + ".material") || config.contains(premiumPath + ".command")) {
                    Reward reward = loadSingleReward(premiumPath, i, false);
                    if (reward != null) {
                        premiumRewards.add(reward);
                        premiumLevel.add(reward);
                    }
                } else if (config.contains(premiumPath + ".items")) {
                    for (String key : config.getConfigurationSection(premiumPath + ".items").getKeys(false)) {
                        Reward reward = loadSingleReward(premiumPath + ".items." + key, i, false);
                        if (reward != null) {
                            premiumRewards.add(reward);
                            premiumLevel.add(reward);
                        }
                    }
                }
            }

            if (!freeLevel.isEmpty()) freeRewardsByLevel.put(i, freeLevel);
            if (!premiumLevel.isEmpty()) premiumRewardsByLevel.put(i, premiumLevel);
        }
    }

    private Reward loadSingleReward(String path, int level, boolean isFree) {
        if (config.contains(path + ".command")) {
            String command = config.getString(path + ".command");
            String displayName = config.getString(path + ".display", "Mystery Reward");
            return new Reward(level, command, displayName, isFree);
        } else if (config.contains(path + ".material")) {
            String material = config.getString(path + ".material", "DIRT");
            int amount = config.getInt(path + ".amount", 1);

            try {
                Material mat = Material.valueOf(material.toUpperCase());
                return new Reward(level, mat, amount, isFree);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material for " + (isFree ? "free" : "premium") +
                        " level " + level + ": " + material);
                return null;
            }
        }
        return null;
    }

    private void generateDailyMissions() {
        List<Mission> newMissions = new ArrayList<>();
        ConfigurationSection pools = missionsConfig.getConfigurationSection("mission-pools");

        if (pools == null) {
            getLogger().warning("No mission pools found in missions.yml!");
            return;
        }

        List<MissionTemplate> templates = new ArrayList<>();

        for (String key : pools.getKeys(false)) {
            ConfigurationSection missionSection = pools.getConfigurationSection(key);
            if (missionSection == null) continue;

            String type = missionSection.getString("type");
            String target = missionSection.getString("target");
            String displayName = missionSection.getString("display-name");
            int minRequired = missionSection.getInt("min-required");
            int maxRequired = missionSection.getInt("max-required");
            int minXP = missionSection.getInt("min-xp");
            int maxXP = missionSection.getInt("max-xp");
            int weight = missionSection.getInt("weight", 10);

            for (int i = 0; i < weight; i++) {
                templates.add(new MissionTemplate(displayName, type, target,
                        minRequired, maxRequired, minXP, maxXP));
            }
        }

        if (templates.isEmpty()) {
            getLogger().warning("No valid missions found in missions.yml!");
            return;
        }

        Collections.shuffle(templates);

        for (int i = 0; i < dailyMissionsCount && i < templates.size(); i++) {
            MissionTemplate template = templates.get(i);
            int required = ThreadLocalRandom.current().nextInt(
                    template.minRequired, template.maxRequired + 1);
            int xpReward = ThreadLocalRandom.current().nextInt(
                    template.minXP, template.maxXP + 1);

            String name = template.nameFormat
                    .replace("<amount>", String.valueOf(required))
                    .replace("<target>", formatTarget(template.target));

            newMissions.add(new Mission(name, template.type, template.target,
                    required, xpReward));
        }

        dailyMissions = newMissions;
    }

    private String formatTarget(String target) {
        if (target.equals("ANY")) {
            return "";
        }
        return target.toLowerCase().replace("_", " ");
    }

    private void calculateNextReset() {
        LocalDateTime now = LocalDateTime.now();
        nextMissionReset = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
    }

    private void checkMissionReset() {
        if (LocalDateTime.now().isAfter(nextMissionReset)) {
            generateDailyMissions();
            calculateNextReset();

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(getPrefix() + getMessage("messages.mission.reset"));
            }

            for (PlayerData data : playerCache.values()) {
                data.missionProgress.clear();
            }
        }
    }

    private String getTimeUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        long hours = ChronoUnit.HOURS.between(now, nextMissionReset);
        long minutes = ChronoUnit.MINUTES.between(now, nextMissionReset) % 60;

        String hourStr = hours == 1 ? getMessage("time.hour") : getMessage("time.hours");
        String minuteStr = minutes == 1 ? getMessage("time.minute") : getMessage("time.minutes");

        return getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    private PlayerData getPlayerData(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data == null) {
            data = loadPlayerSync(uuid);
            playerCache.put(uuid, data);
        }
        return data;
    }

    private void loadPlayerAsync(UUID uuid) {
        CompletableFuture.supplyAsync(() -> loadPlayerSync(uuid), databaseExecutor)
                .thenAccept(data -> playerCache.put(uuid, data));
    }

    private PlayerData loadPlayerSync(UUID uuid) {
        PlayerData data = new PlayerData(uuid);

        try {
            selectPlayerStmt.setString(1, uuid.toString());
            ResultSet rs = selectPlayerStmt.executeQuery();

            if (rs.next()) {
                data.xp = rs.getInt("xp");
                data.level = rs.getInt("level");
                data.lastNotification = rs.getInt("last_notification");
                data.totalLevels = rs.getInt("total_levels");
                data.hasPremium = rs.getInt("has_premium") == 1;

                String claimedFree = rs.getString("claimed_free");
                if (!claimedFree.isEmpty()) {
                    for (String level : claimedFree.split(",")) {
                        if (!level.isEmpty()) {
                            data.claimedFreeRewards.add(Integer.parseInt(level));
                        }
                    }
                }

                String claimedPremium = rs.getString("claimed_premium");
                if (!claimedPremium.isEmpty()) {
                    for (String level : claimedPremium.split(",")) {
                        if (!level.isEmpty()) {
                            data.claimedPremiumRewards.add(Integer.parseInt(level));
                        }
                    }
                }
            } else {
                insertPlayerStmt.setString(1, uuid.toString());
                insertPlayerStmt.executeUpdate();
            }
            rs.close();

            String today = LocalDateTime.now().toLocalDate().toString();
            PreparedStatement missionPs = connection.prepareStatement(
                    "SELECT * FROM missions WHERE uuid = ? AND date = ?"
            );
            missionPs.setString(1, uuid.toString());
            missionPs.setString(2, today);
            ResultSet missionRs = missionPs.executeQuery();

            while (missionRs.next()) {
                data.missionProgress.put(missionRs.getString("mission"),
                        missionRs.getInt("progress"));
            }

            missionRs.close();
            missionPs.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return data;
    }

    private void markForSave(UUID uuid) {
        pendingSaves.put(uuid, System.currentTimeMillis());
    }

    private void performBatchSave() {
        if (pendingSaves.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        Map<UUID, PlayerData> toSave = new HashMap<>();

        Iterator<Map.Entry<UUID, Long>> it = pendingSaves.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (currentTime - entry.getValue() >= SAVE_DELAY) {
                PlayerData data = playerCache.get(entry.getKey());
                if (data != null) {
                    toSave.put(entry.getKey(), data);
                }
                it.remove();
            }
        }

        if (toSave.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            String today = LocalDateTime.now().toLocalDate().toString();

            try {
                connection.setAutoCommit(false);

                for (Map.Entry<UUID, PlayerData> entry : toSave.entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerData data = entry.getValue();

                    updatePlayerStmt.setInt(1, data.xp);
                    updatePlayerStmt.setInt(2, data.level);
                    updatePlayerStmt.setString(3, String.join(",",
                            data.claimedFreeRewards.stream().map(String::valueOf).toArray(String[]::new)));
                    updatePlayerStmt.setString(4, String.join(",",
                            data.claimedPremiumRewards.stream().map(String::valueOf).toArray(String[]::new)));
                    updatePlayerStmt.setInt(5, data.lastNotification);
                    updatePlayerStmt.setInt(6, data.totalLevels);
                    updatePlayerStmt.setInt(7, data.hasPremium ? 1 : 0);
                    updatePlayerStmt.setString(8, uuid.toString());
                    updatePlayerStmt.addBatch();

                    for (Map.Entry<String, Integer> mission : data.missionProgress.entrySet()) {
                        insertMissionStmt.setString(1, uuid.toString());
                        insertMissionStmt.setString(2, mission.getKey());
                        insertMissionStmt.setInt(3, mission.getValue());
                        insertMissionStmt.setString(4, today);
                        insertMissionStmt.addBatch();
                    }
                }

                updatePlayerStmt.executeBatch();
                insertMissionStmt.executeBatch();
                connection.commit();

            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }, databaseExecutor);
    }

    private void saveAllPlayers() {
        for (UUID uuid : playerCache.keySet()) {
            pendingSaves.put(uuid, 0L);
        }
        performBatchSave();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        playTimeStart.put(uuid, System.currentTimeMillis());
        lastLocations.put(uuid, player.getLocation());

        loadPlayerAsync(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerData data = playerCache.get(uuid);
                if (data != null) {
                    int available = countAvailableRewards(player, data);
                    if (available > 0) {
                        player.sendMessage(getPrefix() + getMessage("messages.rewards-available",
                                "%amount%", String.valueOf(available)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskLater(this, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Long startTime = playTimeStart.remove(uuid);
        if (startTime != null) {
            long playTime = (System.currentTimeMillis() - startTime) / 60000;
            if (playTime > 0) {
                progressMission(event.getPlayer(), "PLAY_TIME", "ANY", (int) playTime);
            }
        }

        markForSave(uuid);

        currentPages.remove(event.getPlayer().getEntityId());
        lastLocations.remove(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!Bukkit.getOfflinePlayer(uuid).isOnline()) {
                    playerCache.remove(uuid);
                }
            }
        }.runTaskLater(this, 200L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location last = lastLocations.get(uuid);
        if (last != null) {
            double distance = last.distance(event.getTo());
            if (distance >= 1) {
                progressMission(player, "WALK_DISTANCE", "ANY", (int) distance);
                lastLocations.put(uuid, event.getTo());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        progressMission(event.getEntity(), "DEATH", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            progressMission(player, "DAMAGE_DEALT", "ANY", (int) event.getDamage());
        }

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            progressMission(player, "DAMAGE_TAKEN", "ANY", (int) event.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            String entityType = event.getEntity().getType().name();
            progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player) {
            Player player = (Player) event.getOwner();
            String entityType = event.getEntity().getType().name();
            progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Player player = event.getPlayer();
            progressMission(player, "TRADE_VILLAGER", "ANY", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        progressMission(event.getEnchanter(), "ENCHANT_ITEM", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            Player player = event.getPlayer();
            if (event.getCaught() instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item item = (org.bukkit.entity.Item) event.getCaught();
                String itemType = item.getItemStack().getType().name();
                progressMission(player, "FISH_ITEM", itemType, item.getItemStack().getAmount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack result = event.getRecipe().getResult();
            String itemType = result.getType().name();
            progressMission(player, "CRAFT_ITEM", itemType, result.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        progressMission(player, "PLACE_BLOCK", blockType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();

            if (event.getEntity() instanceof Player) {
                progressMission(killer, "KILL_PLAYER", "PLAYER", 1);
            } else {
                String entityType = event.getEntityType().name();
                progressMission(killer, "KILL_MOB", entityType, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        Material mat = event.getBlock().getType();

        progressMission(player, "BREAK_BLOCK", blockType, 1);

        if (event.isDropItems() && oreTypes.contains(mat)) {
            progressMission(player, "MINE_BLOCK", blockType, 1);
        }
    }

    private void updatePlayTime() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long startTime = playTimeStart.get(uuid);

            if (startTime != null) {
                long playTime = (currentTime - startTime) / 60000;
                if (playTime > 0) {
                    progressMission(player, "PLAY_TIME", "ANY", (int) playTime);
                    playTimeStart.put(uuid, currentTime);
                }
            }
        }
    }

    private void progressMission(Player player, String type, String target, int amount) {
        PlayerData data = playerCache.get(player.getUniqueId());
        if (data == null) return;

        boolean changed = false;
        List<Mission> currentMissions = new ArrayList<>(dailyMissions);

        for (Mission mission : currentMissions) {
            if (mission.type.equals(type)) {
                if (mission.target.equals("ANY") || mission.target.equals(target)) {
                    String key = mission.name.toLowerCase().replace(" ", "_");
                    int current = data.missionProgress.getOrDefault(key, 0);

                    if (current < mission.required) {
                        current = Math.min(current + amount, mission.required);
                        data.missionProgress.put(key, current);
                        changed = true;

                        if (current >= mission.required) {
                            data.xp += mission.xpReward;
                            checkLevelUp(player, data);

                            player.sendMessage(getPrefix() + getMessage("messages.mission.completed",
                                    "%mission%", mission.name,
                                    "%reward_xp%", String.valueOf(mission.xpReward)));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }

        if (changed) {
            markForSave(player.getUniqueId());
        }
    }

    private void checkLevelUp(Player player, PlayerData data) {
        boolean leveled = false;

        while (data.xp >= xpPerLevel && data.level < 54) {
            data.xp -= xpPerLevel;
            data.level++;
            data.totalLevels++;
            leveled = true;

            player.sendMessage(getPrefix() + getMessage("messages.level-up",
                    "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage(getPrefix() + getMessage("messages.new-rewards"));
            }
        }

        if (leveled) {
            markForSave(player.getUniqueId());
        }
    }

    private void openBattlePassGUI(Player player, int page) {
        String title = getMessage("gui.battlepass", "%page%", String.valueOf(page));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = getPlayerData(player.getUniqueId());

        currentPages.put(player.getEntityId(), page);

        ItemStack info = getCachedItem("info_star", () -> {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.progress.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta infoMeta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        String premiumStatus = data.hasPremium ?
                getMessage("items.premium-status.active") :
                getMessage("items.premium-status.inactive");

        for (String line : messagesConfig.getStringList("items.progress.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%level%", String.valueOf(data.level))
                    .replace("%xp%", String.valueOf(data.xp))
                    .replace("%xp_needed%", String.valueOf(xpPerLevel))
                    .replace("%premium_status%", premiumStatus)
                    .replace("%season_time%", getTimeUntilSeasonEnd())));
        }
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        int startLevel = (page - 1) * 9 + 1;
        int endLevel = Math.min(startLevel + 8, 54);

        for (int i = 0; i <= 8 && startLevel + i <= 54; i++) {
            int level = startLevel + i;

            List<Reward> premiumLevel = premiumRewardsByLevel.get(level);
            if (premiumLevel != null && !premiumLevel.isEmpty()) {
                ItemStack premiumItem = createRewardItem(premiumLevel, level, data,
                        data.hasPremium, true);
                gui.setItem(9 + i, premiumItem);
            }

            List<Reward> freeLevel = freeRewardsByLevel.get(level);
            if (freeLevel != null && !freeLevel.isEmpty()) {
                ItemStack freeItem = createRewardItem(freeLevel, level, data, true, false);
                gui.setItem(27 + i, freeItem);
            }
        }

        ItemStack separator = getCachedItem("separator", () -> {
            ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.separator.name"));
            item.setItemMeta(meta);
            return item;
        });

        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }

        if (page > 1) {
            gui.setItem(45, createNavigationItem(false, page - 1));
        }

        if (endLevel < 54) {
            gui.setItem(53, createNavigationItem(true, page + 1));
        }

        ItemStack missions = getCachedItem("missions_book", () -> {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.missions-button.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta missionsMeta = missions.getItemMeta();
        List<String> missionsLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.missions-button.lore")) {
            missionsLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", getTimeUntilReset())));
        }
        missionsMeta.setLore(missionsLore);
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = getCachedItem("leaderboard_helmet", () -> {
            ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.leaderboard-button.name"));
            List<String> lboardLore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.leaderboard-button.lore")) {
                lboardLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lboardLore);
            item.setItemMeta(meta);
            return item;
        });

        gui.setItem(48, leaderboard);

        player.openInventory(gui);
    }

    private ItemStack createNavigationItem(boolean next, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (next) {
            meta.setDisplayName(getMessage("items.next-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.next-page.lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(targetPage))));
            }
            meta.setLore(lore);
        } else {
            meta.setDisplayName(getMessage("items.previous-page.name"));
            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.previous-page.lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(targetPage))));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getCachedItem(String key, Supplier<ItemStack> supplier) {
        return itemCache.computeIfAbsent(key, k -> supplier.get()).clone();
    }

    private void openLeaderboardGUI(Player player) {
        String title = getMessage("gui.leaderboard");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack titleItem = getCachedItem("leaderboard_title", () -> {
            ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.leaderboard-title.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta titleMeta = titleItem.getItemMeta();
        List<String> titleLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.leaderboard-title.lore")) {
            titleLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%season_time%", getTimeUntilSeasonEnd())));
        }
        titleMeta.setLore(titleLore);
        titleItem.setItemMeta(titleMeta);
        gui.setItem(4, titleItem);

        getTop10PlayersAsync().thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(this, () -> {
                int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

                for (int i = 0; i < topPlayers.size() && i < 10; i++) {
                    PlayerData topPlayer = topPlayers.get(i);
                    String playerName = Bukkit.getOfflinePlayer(topPlayer.uuid).getName();

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(topPlayer.uuid));

                    String rank;
                    if (i == 0) rank = getMessage("items.leaderboard-rank.first");
                    else if (i == 1) rank = getMessage("items.leaderboard-rank.second");
                    else if (i == 2) rank = getMessage("items.leaderboard-rank.third");
                    else rank = getMessage("items.leaderboard-rank.other", "%rank%", String.valueOf(i + 1));

                    skullMeta.setDisplayName(getMessage("items.leaderboard-player.name",
                            "%rank%", rank, "%player%", playerName));

                    String status = topPlayer.uuid.equals(player.getUniqueId()) ?
                            getMessage("items.leaderboard-status.you") :
                            getMessage("items.leaderboard-status.other");

                    List<String> skullLore = new ArrayList<>();
                    for (String line : messagesConfig.getStringList("items.leaderboard-player.lore")) {
                        skullLore.add(ChatColor.translateAlternateColorCodes('&', line
                                .replace("%level%", String.valueOf(topPlayer.level))
                                .replace("%total_levels%", String.valueOf(topPlayer.totalLevels))
                                .replace("%xp%", String.valueOf(topPlayer.xp))
                                .replace("%status%", status)));
                    }
                    skullMeta.setLore(skullLore);
                    skull.setItemMeta(skullMeta);
                    gui.setItem(slots[i], skull);
                }

                if (player.getOpenInventory().getTitle().equals(title)) {
                    player.updateInventory();
                }
            });
        });

        ItemStack back = getCachedItem("back_barrier", () -> {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.back-button.name"));
            List<String> backLore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.back-button.lore")) {
                backLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(backLore);
            item.setItemMeta(meta);
            return item;
        });

        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private void openMissionsGUI(Player player) {
        String title = getMessage("gui.missions");
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = getPlayerData(player.getUniqueId());

        ItemStack timer = getCachedItem("mission_timer", () -> {
            ItemStack item = new ItemStack(Material.CLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.mission-timer.name"));
            item.setItemMeta(meta);
            return item;
        });

        ItemMeta timerMeta = timer.getItemMeta();
        List<String> timerLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.mission-timer.lore")) {
            timerLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", getTimeUntilReset())));
        }
        timerMeta.setLore(timerLore);
        timer.setItemMeta(timerMeta);
        gui.setItem(4, timer);

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        List<Mission> currentMissions = new ArrayList<>(dailyMissions);

        for (int i = 0; i < currentMissions.size() && i < slots.length; i++) {
            Mission mission = currentMissions.get(i);
            String key = mission.name.toLowerCase().replace(" ", "_");
            int progress = data.missionProgress.getOrDefault(key, 0);
            boolean completed = progress >= mission.required;

            ItemStack missionItem = new ItemStack(completed ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta missionMeta = missionItem.getItemMeta();

            String itemName = completed ? "items.mission-completed.name" : "items.mission-in-progress.name";
            String itemLore = completed ? "items.mission-completed.lore" : "items.mission-in-progress.lore";

            missionMeta.setDisplayName(getMessage(itemName, "%mission%", mission.name));

            List<String> missionLore = new ArrayList<>();
            for (String line : messagesConfig.getStringList(itemLore)) {
                missionLore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%progress%", String.valueOf(progress))
                        .replace("%required%", String.valueOf(mission.required))
                        .replace("%reward_xp%", String.valueOf(mission.xpReward))
                        .replace("%reset_time%", getTimeUntilReset())));
            }
            missionMeta.setLore(missionLore);
            missionItem.setItemMeta(missionMeta);
            gui.setItem(slots[i], missionItem);
        }

        ItemStack back = itemCache.get("back_barrier").clone();
        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private ItemStack createRewardItem(List<Reward> rewards, int level, PlayerData data,
                                       boolean hasAccess, boolean isPremium) {
        Set<Integer> claimedSet = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;
        ItemStack item;
        ItemMeta meta;

        String rewardType = getMessage(isPremium ? "reward-types.premium" : "reward-types.free");

        if (data.level >= level && hasAccess && !claimedSet.contains(level)) {
            item = new ItemStack(Material.CHEST_MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-available.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-available");
            meta.setLore(lore);

        } else if (claimedSet.contains(level)) {
            Reward displayReward = rewards.stream()
                    .filter(r -> r.material != Material.COMMAND_BLOCK)
                    .findFirst()
                    .orElse(rewards.get(0));

            item = new ItemStack(displayReward.material, displayReward.amount);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-claimed.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-claimed");
            meta.setLore(lore);

        } else if (!hasAccess && isPremium) {
            item = new ItemStack(Material.MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(level)));

            List<String> lore = createRewardLore(rewards, "items.reward-premium-locked");
            meta.setLore(lore);

        } else {
            item = new ItemStack(Material.MINECART);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-level-locked.name",
                    "%level%", String.valueOf(level),
                    "%type%", rewardType));

            List<String> lore = createRewardLore(rewards, "items.reward-level-locked");
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private List<String> createRewardLore(List<Reward> rewards, String configPath) {
        List<String> lore = new ArrayList<>();

        for (String line : messagesConfig.getStringList(configPath + ".lore-header")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        for (Reward r : rewards) {
            if (r.command != null) {
                lore.add(getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
            } else {
                lore.add(getMessage("messages.rewards.item-reward",
                        "%amount%", String.valueOf(r.amount),
                        "%item%", formatMaterial(r.material)));
            }
        }

        for (String line : messagesConfig.getStringList(configPath + ".lore-footer")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%level%", String.valueOf(rewards.get(0).level))
                    .replace("%season_time%", getTimeUntilSeasonEnd())));
        }

        return lore;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        boolean isBattlePassGUI = false;
        for (int i = 1; i <= 6; i++) {
            if (title.equals(getMessage("gui.battlepass", "%page%", String.valueOf(i)))) {
                isBattlePassGUI = true;
                break;
            }
        }

        if (!isBattlePassGUI && !title.equals(getMessage("gui.leaderboard")) &&
                !title.equals(getMessage("gui.missions"))) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (isBattlePassGUI) {
            handleBattlePassClick(player, clicked, event.getSlot());
        } else if (title.equals(getMessage("gui.leaderboard"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = currentPages.getOrDefault(player.getEntityId(), 1);
                openBattlePassGUI(player, page);
            }
        } else if (title.equals(getMessage("gui.missions"))) {
            if (clicked.getType() == Material.BARRIER) {
                int page = currentPages.getOrDefault(player.getEntityId(), 1);
                openBattlePassGUI(player, page);
            }
        }
    }

    private void handleBattlePassClick(Player player, ItemStack clicked, int slot) {
        int currentPage = currentPages.getOrDefault(player.getEntityId(), 1);

        switch (clicked.getType()) {
            case ARROW:
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName.contains(ChatColor.stripColor(getMessage("items.previous-page.name")).substring(0, Math.min(8, ChatColor.stripColor(getMessage("items.previous-page.name")).length())))) {
                    openBattlePassGUI(player, currentPage - 1);
                } else if (displayName.contains(ChatColor.stripColor(getMessage("items.next-page.name")).substring(0, Math.min(8, ChatColor.stripColor(getMessage("items.next-page.name")).length())))) {
                    openBattlePassGUI(player, currentPage + 1);
                }
                break;

            case BOOK:
                openMissionsGUI(player);
                break;

            case GOLDEN_HELMET:
                openLeaderboardGUI(player);
                break;

            case CHEST_MINECART:
                handleRewardClaim(player, slot, currentPage);
                break;
        }
    }

    private void handleRewardClaim(Player player, int slot, int currentPage) {
        PlayerData data = getPlayerData(player.getUniqueId());
        int startLevel = (currentPage - 1) * 9 + 1;

        if (slot >= 9 && slot <= 17) {
            int index = slot - 9;
            int level = startLevel + index;

            if (!data.hasPremium) {
                player.sendMessage(getPrefix() + getMessage("messages.premium.required"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            List<Reward> levelRewards = premiumRewardsByLevel.get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                    claimRewards(player, data, levelRewards, level, true);
                } else {
                    player.sendMessage(getPrefix() + getMessage("messages.rewards.cannot-claim"));
                }
            }

        } else if (slot >= 27 && slot <= 35) {
            int index = slot - 27;
            int level = startLevel + index;

            List<Reward> levelRewards = freeRewardsByLevel.get(level);
            if (levelRewards != null && !levelRewards.isEmpty()) {
                if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                    claimRewards(player, data, levelRewards, level, false);
                }
            }
        }
    }

    private void claimRewards(Player player, PlayerData data, List<Reward> rewards,
                              int level, boolean isPremium) {
        if (isPremium) {
            data.claimedPremiumRewards.add(level);
        } else {
            data.claimedFreeRewards.add(level);
        }

        StringBuilder message = new StringBuilder(getPrefix() + getMessage(
                isPremium ? "messages.rewards.premium-claimed" : "messages.rewards.free-claimed"));

        for (Reward reward : rewards) {
            if (reward.command != null) {
                String command = reward.command.replace("<player>", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                message.append("\n").append(getMessage("messages.rewards.command-reward",
                        "%reward%", reward.displayName));
            } else {
                player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                message.append("\n").append(getMessage("messages.rewards.item-reward",
                        "%amount%", String.valueOf(reward.amount),
                        "%item%", formatMaterial(reward.material)));
            }
        }

        player.sendMessage(message.toString());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        markForSave(player.getUniqueId());

        openBattlePassGUI(player, currentPages.getOrDefault(player.getEntityId(), 1));
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    private static class PlayerData {
        final UUID uuid;
        int xp = 0;
        int level = 1;
        int lastNotification = 0;
        int totalLevels = 0;
        boolean hasPremium = false;
        final Map<String, Integer> missionProgress = new ConcurrentHashMap<>();
        final Set<Integer> claimedFreeRewards = ConcurrentHashMap.newKeySet();
        final Set<Integer> claimedPremiumRewards = ConcurrentHashMap.newKeySet();

        PlayerData(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static class Mission {
        final String name;
        final String type;
        final String target;
        final int required;
        final int xpReward;

        Mission(String name, String type, String target, int required, int xpReward) {
            this.name = name;
            this.type = type;
            this.target = target;
            this.required = required;
            this.xpReward = xpReward;
        }
    }

    private static class MissionTemplate {
        final String nameFormat;
        final String type;
        final String target;
        final int minRequired;
        final int maxRequired;
        final int minXP;
        final int maxXP;

        MissionTemplate(String nameFormat, String type, String target, int minRequired,
                        int maxRequired, int minXP, int maxXP) {
            this.nameFormat = nameFormat;
            this.type = type;
            this.target = target;
            this.minRequired = minRequired;
            this.maxRequired = maxRequired;
            this.minXP = minXP;
            this.maxXP = maxXP;
        }
    }

    private static class Reward {
        final int level;
        final Material material;
        final int amount;
        final boolean isFree;
        final String command;
        final String displayName;

        Reward(int level, Material material, int amount, boolean isFree) {
            this.level = level;
            this.material = material;
            this.amount = amount;
            this.isFree = isFree;
            this.command = null;
            this.displayName = amount + "x " + formatMaterialStatic(material);
        }

        Reward(int level, String command, String displayName, boolean isFree) {
            this.level = level;
            this.material = Material.COMMAND_BLOCK;
            this.amount = 1;
            this.isFree = isFree;
            this.command = command;
            this.displayName = displayName;
        }

        private static String formatMaterialStatic(Material material) {
            return material.name().toLowerCase().replace("_", " ");
        }
    }
}