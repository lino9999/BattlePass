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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BattlePass extends JavaPlugin implements Listener, CommandExecutor {

    private Connection connection;
    private final Map<UUID, PlayerData> playerCache = new HashMap<>();
    private final Map<Integer, Integer> currentPages = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> playTimeStart = new HashMap<>();
    private List<Mission> dailyMissions = new ArrayList<>();
    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private FileConfiguration config;
    private FileConfiguration missionsConfig;
    private FileConfiguration messagesConfig;
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;
    private int seasonDuration = 30;
    private int xpPerLevel = 200;
    private int dailyMissionsCount = 7;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("missions.yml", false);
        saveResource("messages.yml", false);

        loadConfigurations();

        getServer().getPluginManager().registerEvents(this, this);
        initDatabase();
        loadSeasonData();
        loadRewardsFromConfig();
        generateDailyMissions();
        calculateNextReset();

        getCommand("battlepass").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllPlayers();
                checkMissionReset();
                checkSeasonReset();
                checkRewardNotifications();
                updatePlayTime();
            }
        }.runTaskTimer(this, 6000L, 1200L);

        getLogger().info(getMessage("messages.plugin-enabled"));
    }

    @Override
    public void onDisable() {
        saveAllPlayers();
        saveSeasonData();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
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

        if (replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i].toString(), replacements[i + 1].toString());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("prefix", "&6&l[BATTLE PASS] &e"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("help")) {
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
                return true;
            } else if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("battlepass.admin")) {
                reloadConfig();
                loadConfigurations();
                loadRewardsFromConfig();
                generateDailyMissions();
                sender.sendMessage(getPrefix() + getMessage("messages.config-reloaded"));
                return true;
            } else if (args[0].equalsIgnoreCase("addpremium") && sender.hasPermission("battlepass.admin")) {
                if (args.length < 2) {
                    sender.sendMessage(getPrefix() + getMessage("messages.usage.add-premium"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(getPrefix() + getMessage("messages.player-not-found"));
                    return true;
                }

                PlayerData data = loadPlayer(target.getUniqueId());
                data.hasPremium = true;
                savePlayer(target.getUniqueId());

                sender.sendMessage(getPrefix() + getMessage("messages.premium.given-sender", "%target%", target.getName()));
                target.sendMessage(getPrefix() + getMessage("messages.premium.given-target"));
                target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                return true;

            } else if (args[0].equalsIgnoreCase("removepremium") && sender.hasPermission("battlepass.admin")) {
                if (args.length < 2) {
                    sender.sendMessage(getPrefix() + getMessage("messages.usage.remove-premium"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(getPrefix() + getMessage("messages.player-not-found"));
                    return true;
                }

                PlayerData data = loadPlayer(target.getUniqueId());
                data.hasPremium = false;
                savePlayer(target.getUniqueId());

                sender.sendMessage(getPrefix() + getMessage("messages.premium.removed-sender", "%target%", target.getName()));
                target.sendMessage(getPrefix() + getMessage("messages.premium.removed-target"));
                return true;
            } else if (args[0].equalsIgnoreCase("addxp") && sender.hasPermission("battlepass.admin")) {
                if (args.length < 3) {
                    sender.sendMessage(getPrefix() + getMessage("messages.usage.add-xp"));
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

                    PlayerData data = loadPlayer(target.getUniqueId());
                    data.xp += amount;
                    checkLevelUp(target, data);
                    savePlayer(target.getUniqueId());

                    sender.sendMessage(getPrefix() + getMessage("messages.xp.added-sender", "%amount%", String.valueOf(amount), "%target%", target.getName()));
                    target.sendMessage(getPrefix() + getMessage("messages.xp.added-target", "%amount%", String.valueOf(amount)));
                    return true;
                } catch (NumberFormatException e) {
                    sender.sendMessage(getPrefix() + getMessage("messages.invalid-amount"));
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("removexp") && sender.hasPermission("battlepass.admin")) {
                if (args.length < 3) {
                    sender.sendMessage(getPrefix() + getMessage("messages.usage.remove-xp"));
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

                    PlayerData data = loadPlayer(target.getUniqueId());
                    int totalXP = (data.level - 1) * xpPerLevel + data.xp;
                    totalXP = Math.max(0, totalXP - amount);

                    int newLevel = 1;
                    while (totalXP >= xpPerLevel && newLevel < 54) {
                        totalXP -= xpPerLevel;
                        newLevel++;
                    }
                    data.level = newLevel;
                    data.xp = totalXP;

                    savePlayer(target.getUniqueId());

                    sender.sendMessage(getPrefix() + getMessage("messages.xp.removed-sender", "%amount%", String.valueOf(amount), "%target%", target.getName()));
                    target.sendMessage(getPrefix() + getMessage("messages.xp.removed-target", "%amount%", String.valueOf(amount)));
                    return true;
                } catch (NumberFormatException e) {
                    sender.sendMessage(getPrefix() + getMessage("messages.invalid-amount"));
                    return true;
                }
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("messages.player-only"));
            return true;
        }

        Player player = (Player) sender;
        openBattlePassGUI(player, 1);
        return true;
    }

    private void initDatabase() {
        try {
            File dbFile = new File(getDataFolder(), "battlepass.db");
            if (!dbFile.exists()) {
                getDataFolder().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            Statement stmt = connection.createStatement();
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

            ResultSet rs = stmt.executeQuery("PRAGMA table_info(players)");
            boolean hasPremiumExists = false;
            while (rs.next()) {
                if (rs.getString("name").equals("has_premium")) {
                    hasPremiumExists = true;
                    break;
                }
            }
            rs.close();

            if (!hasPremiumExists) {
                stmt.executeUpdate("ALTER TABLE players ADD COLUMN has_premium INTEGER DEFAULT 0");
                getLogger().info("Added has_premium column to database");
            }

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadSeasonData() {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM season_data WHERE id = 1");
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                seasonEndDate = LocalDateTime.parse(rs.getString("end_date"));
                seasonDuration = rs.getInt("duration");

                if (LocalDateTime.now().isAfter(seasonEndDate)) {
                    resetSeason();
                }
            } else {
                seasonDuration = config.getInt("season.duration", 30);
                seasonEndDate = LocalDateTime.now().plusDays(seasonDuration);
                saveSeasonData();
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            seasonDuration = 30;
            seasonEndDate = LocalDateTime.now().plusDays(seasonDuration);
        }
    }

    private void saveSeasonData() {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO season_data (id, end_date, duration) VALUES (1, ?, ?)"
            );
            ps.setString(1, seasonEndDate.toString());
            ps.setInt(2, seasonDuration);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("UPDATE players SET xp = 0, level = 1, claimed_free = '', claimed_premium = '', has_premium = 0");
            stmt.executeUpdate("DELETE FROM missions");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        playerCache.clear();
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

        return getMessage("time.days-hours", "%days%", String.valueOf(days), "%hours%", String.valueOf(hours));
    }

    private void checkRewardNotifications() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = loadPlayer(player.getUniqueId());
            int availableRewards = countAvailableRewards(player, data);

            if (availableRewards > data.lastNotification) {
                player.sendMessage(getPrefix() + getMessage("messages.rewards-notification",
                        "%amount%", String.valueOf(availableRewards - data.lastNotification)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                data.lastNotification = availableRewards;
            }
        }
    }

    private int countAvailableRewards(Player player, PlayerData data) {
        int count = 0;
        boolean hasPremium = data.hasPremium;

        Set<Integer> freeLevels = new HashSet<>();
        for (Reward reward : freeRewards) {
            if (data.level >= reward.level && !data.claimedFreeRewards.contains(reward.level)) {
                freeLevels.add(reward.level);
            }
        }
        count += freeLevels.size();

        if (hasPremium) {
            Set<Integer> premiumLevels = new HashSet<>();
            for (Reward reward : premiumRewards) {
                if (data.level >= reward.level && !data.claimedPremiumRewards.contains(reward.level)) {
                    premiumLevels.add(reward.level);
                }
            }
            count += premiumLevels.size();
        }

        return count;
    }

    private List<PlayerData> getTop10Players() {
        List<PlayerData> allPlayers = new ArrayList<>();

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM players ORDER BY total_levels DESC, level DESC, xp DESC LIMIT 10"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
                data.xp = rs.getInt("xp");
                data.level = rs.getInt("level");
                data.totalLevels = rs.getInt("total_levels");
                allPlayers.add(data);
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return allPlayers;
    }

    private void loadRewardsFromConfig() {
        freeRewards.clear();
        premiumRewards.clear();

        for (int i = 1; i <= 54; i++) {
            String freePath = "rewards.free.level-" + i;
            String premiumPath = "rewards.premium.level-" + i;

            if (config.contains(freePath)) {
                if (config.contains(freePath + ".material") || config.contains(freePath + ".command")) {
                    loadSingleReward(freePath, i, true);
                } else if (config.contains(freePath + ".items")) {
                    for (String key : config.getConfigurationSection(freePath + ".items").getKeys(false)) {
                        loadSingleReward(freePath + ".items." + key, i, true);
                    }
                }
            }

            if (config.contains(premiumPath)) {
                if (config.contains(premiumPath + ".material") || config.contains(premiumPath + ".command")) {
                    loadSingleReward(premiumPath, i, false);
                } else if (config.contains(premiumPath + ".items")) {
                    for (String key : config.getConfigurationSection(premiumPath + ".items").getKeys(false)) {
                        loadSingleReward(premiumPath + ".items." + key, i, false);
                    }
                }
            }
        }
    }

    private void loadSingleReward(String path, int level, boolean isFree) {
        if (config.contains(path + ".command")) {
            String command = config.getString(path + ".command");
            String displayName = config.getString(path + ".display", "Mystery Reward");

            Reward reward = new Reward(level, command, displayName, isFree);
            if (isFree) {
                freeRewards.add(reward);
            } else {
                premiumRewards.add(reward);
            }
        } else if (config.contains(path + ".material")) {
            String material = config.getString(path + ".material", "DIRT");
            int amount = config.getInt(path + ".amount", 1);

            try {
                Material mat = Material.valueOf(material.toUpperCase());
                Reward reward = new Reward(level, mat, amount, isFree);
                if (isFree) {
                    freeRewards.add(reward);
                } else {
                    premiumRewards.add(reward);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material for " + (isFree ? "free" : "premium") + " level " + level + ": " + material);
            }
        }
    }

    private void generateDailyMissions() {
        dailyMissions.clear();

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
                templates.add(new MissionTemplate(displayName, type, target, minRequired, maxRequired, minXP, maxXP));
            }
        }

        if (templates.isEmpty()) {
            getLogger().warning("No valid missions found in missions.yml!");
            return;
        }

        Collections.shuffle(templates);

        for (int i = 0; i < dailyMissionsCount && i < templates.size(); i++) {
            MissionTemplate template = templates.get(i);
            int required = ThreadLocalRandom.current().nextInt(template.minRequired, template.maxRequired + 1);
            int xpReward = ThreadLocalRandom.current().nextInt(template.minXP, template.maxXP + 1);

            String name = template.nameFormat
                    .replace("<amount>", String.valueOf(required))
                    .replace("<target>", formatTarget(template.target));

            dailyMissions.add(new Mission(name, template.type, template.target, required, xpReward));
        }
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

        return getMessage("time.hours-minutes", "%hours%", String.valueOf(hours), "%minutes%", String.valueOf(minutes));
    }

    private PlayerData loadPlayer(UUID uuid) {
        if (playerCache.containsKey(uuid)) {
            return playerCache.get(uuid);
        }

        PlayerData data = new PlayerData(uuid);

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                data.xp = rs.getInt("xp");
                data.level = rs.getInt("level");
                data.lastNotification = rs.getInt("last_notification");
                data.totalLevels = rs.getInt("total_levels");
                data.hasPremium = rs.getInt("has_premium") == 1;

                String claimedFree = rs.getString("claimed_free");
                if (!claimedFree.isEmpty()) {
                    for (String level : claimedFree.split(",")) {
                        data.claimedFreeRewards.add(Integer.parseInt(level));
                    }
                }

                String claimedPremium = rs.getString("claimed_premium");
                if (!claimedPremium.isEmpty()) {
                    for (String level : claimedPremium.split(",")) {
                        data.claimedPremiumRewards.add(Integer.parseInt(level));
                    }
                }
            } else {
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels, has_premium) VALUES (?, 0, 1, '', '', 0, 0, 0)"
                );
                insert.setString(1, uuid.toString());
                insert.executeUpdate();
                insert.close();
            }

            rs.close();
            ps.close();

            String today = LocalDateTime.now().toLocalDate().toString();
            PreparedStatement missionPs = connection.prepareStatement(
                    "SELECT * FROM missions WHERE uuid = ? AND date = ?"
            );
            missionPs.setString(1, uuid.toString());
            missionPs.setString(2, today);
            ResultSet missionRs = missionPs.executeQuery();

            while (missionRs.next()) {
                data.missionProgress.put(missionRs.getString("mission"), missionRs.getInt("progress"));
            }

            missionRs.close();
            missionPs.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        playerCache.put(uuid, data);
        return data;
    }

    private void savePlayer(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data == null) return;

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "UPDATE players SET xp = ?, level = ?, claimed_free = ?, claimed_premium = ?, last_notification = ?, total_levels = ?, has_premium = ? WHERE uuid = ?"
            );
            ps.setInt(1, data.xp);
            ps.setInt(2, data.level);
            ps.setString(3, String.join(",", data.claimedFreeRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setString(4, String.join(",", data.claimedPremiumRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setInt(5, data.lastNotification);
            ps.setInt(6, data.totalLevels);
            ps.setInt(7, data.hasPremium ? 1 : 0);
            ps.setString(8, uuid.toString());
            ps.executeUpdate();
            ps.close();

            String today = LocalDateTime.now().toLocalDate().toString();
            for (Map.Entry<String, Integer> entry : data.missionProgress.entrySet()) {
                PreparedStatement missionPs = connection.prepareStatement(
                        "INSERT OR REPLACE INTO missions (uuid, mission, progress, date) VALUES (?, ?, ?, ?)"
                );
                missionPs.setString(1, uuid.toString());
                missionPs.setString(2, entry.getKey());
                missionPs.setInt(3, entry.getValue());
                missionPs.setString(4, today);
                missionPs.executeUpdate();
                missionPs.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveAllPlayers() {
        for (UUID uuid : playerCache.keySet()) {
            savePlayer(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = loadPlayer(player.getUniqueId());

        playTimeStart.put(player.getUniqueId(), System.currentTimeMillis());
        lastLocations.put(player.getUniqueId(), player.getLocation());

        new BukkitRunnable() {
            @Override
            public void run() {
                int available = countAvailableRewards(player, data);
                if (available > 0) {
                    player.sendMessage(getPrefix() + getMessage("messages.rewards-available", "%amount%", String.valueOf(available)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            }
        }.runTaskLater(this, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Update play time on quit
        if (playTimeStart.containsKey(uuid)) {
            long playTime = (System.currentTimeMillis() - playTimeStart.get(uuid)) / 60000; // Convert to minutes
            progressMission(event.getPlayer(), "PLAY_TIME", "ANY", (int) playTime);
            playTimeStart.remove(uuid);
        }

        savePlayer(uuid);
        playerCache.remove(uuid);
        currentPages.remove(event.getPlayer().getEntityId());
        lastLocations.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (lastLocations.containsKey(uuid)) {
            Location last = lastLocations.get(uuid);
            double distance = last.distance(event.getTo());

            if (distance >= 1) {
                progressMission(player, "WALK_DISTANCE", "ANY", (int) distance);
                lastLocations.put(uuid, event.getTo());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        progressMission(event.getEntity(), "DEATH", "ANY", 1);
    }

    @EventHandler
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

    @EventHandler
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            String entityType = event.getEntity().getType().name();
            progressMission(player, "BREED_ANIMAL", entityType, 1);
        }
    }

    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player) {
            Player player = (Player) event.getOwner();
            String entityType = event.getEntity().getType().name();
            progressMission(player, "TAME_ANIMAL", entityType, 1);
        }
    }

    @EventHandler
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Player player = event.getPlayer();
            progressMission(player, "TRADE_VILLAGER", "ANY", 1);
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        progressMission(event.getEnchanter(), "ENCHANT_ITEM", "ANY", 1);
    }

    @EventHandler
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

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack result = event.getRecipe().getResult();
            String itemType = result.getType().name();
            progressMission(player, "CRAFT_ITEM", itemType, result.getAmount());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();
        progressMission(player, "PLACE_BLOCK", blockType, 1);
    }

    @EventHandler
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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name();

        progressMission(player, "BREAK_BLOCK", blockType, 1);

        // For ores, also count as mining if the item drops
        if (event.isDropItems() && isOre(event.getBlock().getType())) {
            progressMission(player, "MINE_BLOCK", blockType, 1);
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private void updatePlayTime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (playTimeStart.containsKey(uuid)) {
                long playTime = (System.currentTimeMillis() - playTimeStart.get(uuid)) / 60000;
                if (playTime > 0) {
                    progressMission(player, "PLAY_TIME", "ANY", (int) playTime);
                    playTimeStart.put(uuid, System.currentTimeMillis());
                }
            }
        }
    }

    private void progressMission(Player player, String type, String target, int amount) {
        PlayerData data = loadPlayer(player.getUniqueId());

        for (Mission mission : dailyMissions) {
            if (mission.type.equals(type)) {
                if (mission.target.equals("ANY") || mission.target.equals(target)) {
                    String key = mission.name.toLowerCase().replace(" ", "_");
                    int current = data.missionProgress.getOrDefault(key, 0);

                    if (current < mission.required) {
                        current = Math.min(current + amount, mission.required);
                        data.missionProgress.put(key, current);

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
    }

    private void checkLevelUp(Player player, PlayerData data) {
        while (data.xp >= xpPerLevel && data.level < 54) {
            data.xp -= xpPerLevel;
            data.level++;
            data.totalLevels++;
            player.sendMessage(getPrefix() + getMessage("messages.level-up", "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage(getPrefix() + getMessage("messages.new-rewards"));
            }
        }
    }

    private void openBattlePassGUI(Player player, int page) {
        String title = getMessage("gui.battlepass", "%page%", String.valueOf(page));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = loadPlayer(player.getUniqueId());
        boolean hasPremium = data.hasPremium;

        currentPages.put(player.getEntityId(), page);

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(getMessage("items.progress.name"));

        List<String> lore = new ArrayList<>();
        String premiumStatus = hasPremium ? getMessage("items.premium-status.active") : getMessage("items.premium-status.inactive");

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

            Reward premium = premiumRewards.stream()
                    .filter(r -> r.level == level)
                    .findFirst()
                    .orElse(null);

            if (premium != null) {
                ItemStack premiumItem = createRewardItem(premium, data, hasPremium, true);
                gui.setItem(9 + i, premiumItem);
            }

            Reward free = freeRewards.stream()
                    .filter(r -> r.level == level)
                    .findFirst()
                    .orElse(null);

            if (free != null) {
                ItemStack freeItem = createRewardItem(free, data, true, false);
                gui.setItem(27 + i, freeItem);
            }
        }

        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta sepMeta = separator.getItemMeta();
        sepMeta.setDisplayName(getMessage("items.separator.name"));
        separator.setItemMeta(sepMeta);
        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }

        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(getMessage("items.previous-page.name"));

            List<String> prevLore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.previous-page.lore")) {
                prevLore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(page - 1))));
            }
            prevMeta.setLore(prevLore);
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (endLevel < 54) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(getMessage("items.next-page.name"));

            List<String> nextLore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.next-page.lore")) {
                nextLore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%page%", String.valueOf(page + 1))));
            }
            nextMeta.setLore(nextLore);
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack missions = new ItemStack(Material.BOOK);
        ItemMeta missionsMeta = missions.getItemMeta();
        missionsMeta.setDisplayName(getMessage("items.missions-button.name"));

        List<String> missionsLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.missions-button.lore")) {
            missionsLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", getTimeUntilReset())));
        }
        missionsMeta.setLore(missionsLore);
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta leaderMeta = leaderboard.getItemMeta();
        leaderMeta.setDisplayName(getMessage("items.leaderboard-button.name"));

        List<String> leaderLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.leaderboard-button.lore")) {
            leaderLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        leaderMeta.setLore(leaderLore);
        leaderboard.setItemMeta(leaderMeta);
        gui.setItem(48, leaderboard);

        player.openInventory(gui);
    }

    private void openLeaderboardGUI(Player player) {
        String title = getMessage("gui.leaderboard");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack title_item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta titleMeta = title_item.getItemMeta();
        titleMeta.setDisplayName(getMessage("items.leaderboard-title.name"));

        List<String> titleLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.leaderboard-title.lore")) {
            titleLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%season_time%", getTimeUntilSeasonEnd())));
        }
        titleMeta.setLore(titleLore);
        title_item.setItemMeta(titleMeta);
        gui.setItem(4, title_item);

        List<PlayerData> topPlayers = getTop10Players();
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

            skullMeta.setDisplayName(getMessage("items.leaderboard-player.name", "%rank%", rank, "%player%", playerName));

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

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(getMessage("items.back-button.name"));

        List<String> backLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.back-button.lore")) {
            backLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        backMeta.setLore(backLore);
        back.setItemMeta(backMeta);
        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private void openMissionsGUI(Player player) {
        String title = getMessage("gui.missions");
        Inventory gui = Bukkit.createInventory(null, 54, title);
        PlayerData data = loadPlayer(player.getUniqueId());

        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta timerMeta = timer.getItemMeta();
        timerMeta.setDisplayName(getMessage("items.mission-timer.name"));

        List<String> timerLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.mission-timer.lore")) {
            timerLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reset_time%", getTimeUntilReset())));
        }
        timerMeta.setLore(timerLore);
        timer.setItemMeta(timerMeta);
        gui.setItem(4, timer);

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < dailyMissions.size() && i < slots.length; i++) {
            Mission mission = dailyMissions.get(i);
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

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(getMessage("items.back-button.name"));

        List<String> backLore = new ArrayList<>();
        for (String line : messagesConfig.getStringList("items.back-button.lore")) {
            backLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        backMeta.setLore(backLore);
        back.setItemMeta(backMeta);
        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private ItemStack createRewardItem(Reward reward, PlayerData data, boolean hasAccess, boolean isPremium) {
        Set<Integer> claimedSet = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;
        ItemStack item;
        ItemMeta meta;

        List<Reward> levelRewards = isPremium ?
                premiumRewards.stream().filter(r -> r.level == reward.level).collect(Collectors.toList()) :
                freeRewards.stream().filter(r -> r.level == reward.level).collect(Collectors.toList());

        String rewardType = getMessage(isPremium ? "reward-types.premium" : "reward-types.free");

        if (data.level >= reward.level && hasAccess && !claimedSet.contains(reward.level)) {
            item = new ItemStack(Material.CHEST_MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-available.name",
                    "%level%", String.valueOf(reward.level),
                    "%type%", rewardType));

            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.reward-available.lore-header")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            for (Reward r : levelRewards) {
                if (r.command != null) {
                    lore.add(getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
                } else {
                    lore.add(getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(r.amount),
                            "%item%", formatMaterial(r.material)));
                }
            }

            for (String line : messagesConfig.getStringList("items.reward-available.lore-footer")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%level%", String.valueOf(reward.level))
                        .replace("%season_time%", getTimeUntilSeasonEnd())));
            }
            meta.setLore(lore);

        } else if (claimedSet.contains(reward.level)) {
            Material displayMat = reward.material;
            int displayAmount = reward.amount;
            for (Reward r : levelRewards) {
                if (r.material != Material.COMMAND_BLOCK) {
                    displayMat = r.material;
                    displayAmount = r.amount;
                    break;
                }
            }

            item = new ItemStack(displayMat, displayAmount);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-claimed.name",
                    "%level%", String.valueOf(reward.level),
                    "%type%", rewardType));

            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.reward-claimed.lore-header")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            for (Reward r : levelRewards) {
                if (r.command != null) {
                    lore.add(getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
                } else {
                    lore.add(getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(r.amount),
                            "%item%", formatMaterial(r.material)));
                }
            }

            for (String line : messagesConfig.getStringList("items.reward-claimed.lore-footer")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%level%", String.valueOf(reward.level))
                        .replace("%season_time%", getTimeUntilSeasonEnd())));
            }
            meta.setLore(lore);

        } else if (!hasAccess && isPremium) {
            item = new ItemStack(Material.MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-premium-locked.name",
                    "%level%", String.valueOf(reward.level)));

            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.reward-premium-locked.lore-header")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            for (Reward r : levelRewards) {
                if (r.command != null) {
                    lore.add(getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
                } else {
                    lore.add(getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(r.amount),
                            "%item%", formatMaterial(r.material)));
                }
            }

            for (String line : messagesConfig.getStringList("items.reward-premium-locked.lore-footer")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%level%", String.valueOf(reward.level))
                        .replace("%season_time%", getTimeUntilSeasonEnd())));
            }
            meta.setLore(lore);

        } else {
            item = new ItemStack(Material.MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName(getMessage("items.reward-level-locked.name",
                    "%level%", String.valueOf(reward.level),
                    "%type%", rewardType));

            List<String> lore = new ArrayList<>();
            for (String line : messagesConfig.getStringList("items.reward-level-locked.lore-header")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            for (Reward r : levelRewards) {
                if (r.command != null) {
                    lore.add(getMessage("messages.rewards.command-reward", "%reward%", r.displayName));
                } else {
                    lore.add(getMessage("messages.rewards.item-reward",
                            "%amount%", String.valueOf(r.amount),
                            "%item%", formatMaterial(r.material)));
                }
            }

            for (String line : messagesConfig.getStringList("items.reward-level-locked.lore-footer")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%level%", String.valueOf(reward.level))
                        .replace("%season_time%", getTimeUntilSeasonEnd())));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(getMessage("gui.battlepass", "%page%", "1")) ||
                title.equals(getMessage("gui.battlepass", "%page%", "2")) ||
                title.equals(getMessage("gui.battlepass", "%page%", "3")) ||
                title.equals(getMessage("gui.battlepass", "%page%", "4")) ||
                title.equals(getMessage("gui.battlepass", "%page%", "5")) ||
                title.equals(getMessage("gui.battlepass", "%page%", "6"))) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            if (clicked == null || !clicked.hasItemMeta()) return;

            if (clicked.getType() == Material.ARROW) {
                String displayName = clicked.getItemMeta().getDisplayName();
                int currentPage = currentPages.getOrDefault(player.getEntityId(), 1);

                if (displayName.contains(ChatColor.stripColor(getMessage("items.previous-page.name")).substring(0, 8))) {
                    openBattlePassGUI(player, currentPage - 1);
                } else if (displayName.contains(ChatColor.stripColor(getMessage("items.next-page.name")).substring(0, 8))) {
                    openBattlePassGUI(player, currentPage + 1);
                }
            } else if (clicked.getType() == Material.BOOK) {
                openMissionsGUI(player);
            } else if (clicked.getType() == Material.GOLDEN_HELMET) {
                openLeaderboardGUI(player);
            } else if (clicked.getType() == Material.CHEST_MINECART) {
                PlayerData data = loadPlayer(player.getUniqueId());
                int slot = event.getSlot();
                int currentPage = currentPages.getOrDefault(player.getEntityId(), 1);
                int startLevel = (currentPage - 1) * 9 + 1;

                if (slot >= 9 && slot <= 17) {
                    int index = slot - 9;
                    int level = startLevel + index;

                    boolean hasPremium = data.hasPremium;

                    if (hasPremium) {
                        List<Reward> levelRewards = premiumRewards.stream()
                                .filter(r -> r.level == level)
                                .collect(Collectors.toList());

                        if (!levelRewards.isEmpty()) {
                            if (data.level >= level && !data.claimedPremiumRewards.contains(level)) {
                                data.claimedPremiumRewards.add(level);

                                StringBuilder message = new StringBuilder(getPrefix() + getMessage("messages.rewards.premium-claimed"));

                                for (Reward reward : levelRewards) {
                                    if (reward.command != null) {
                                        String command = reward.command.replace("<player>", player.getName());
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                        message.append("\n").append(getMessage("messages.rewards.command-reward", "%reward%", reward.displayName));
                                    } else {
                                        player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                                        message.append("\n").append(getMessage("messages.rewards.item-reward",
                                                "%amount%", String.valueOf(reward.amount),
                                                "%item%", formatMaterial(reward.material)));
                                    }
                                }

                                player.sendMessage(message.toString());
                                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                                openBattlePassGUI(player, currentPage);
                            } else {
                                player.sendMessage(getPrefix() + getMessage("messages.rewards.cannot-claim"));
                            }
                        }
                    } else {
                        player.sendMessage(getPrefix() + getMessage("messages.premium.required"));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                } else if (slot >= 27 && slot <= 35) {
                    int index = slot - 27;
                    int level = startLevel + index;

                    List<Reward> levelRewards = freeRewards.stream()
                            .filter(r -> r.level == level)
                            .collect(Collectors.toList());

                    if (!levelRewards.isEmpty()) {
                        if (data.level >= level && !data.claimedFreeRewards.contains(level)) {
                            data.claimedFreeRewards.add(level);

                            StringBuilder message = new StringBuilder(getPrefix() + getMessage("messages.rewards.free-claimed"));

                            for (Reward reward : levelRewards) {
                                if (reward.command != null) {
                                    String command = reward.command.replace("<player>", player.getName());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                    message.append("\n").append(getMessage("messages.rewards.command-reward", "%reward%", reward.displayName));
                                } else {
                                    player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                                    message.append("\n").append(getMessage("messages.rewards.item-reward",
                                            "%amount%", String.valueOf(reward.amount),
                                            "%item%", formatMaterial(reward.material)));
                                }
                            }

                            player.sendMessage(message.toString());
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                            openBattlePassGUI(player, currentPage);
                        }
                    }
                }
            }
        } else if (title.equals(getMessage("gui.leaderboard"))) {
            event.setCancelled(true);

            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                Player player = (Player) event.getWhoClicked();
                int page = currentPages.getOrDefault(player.getEntityId(), 1);
                openBattlePassGUI(player, page);
            }
        } else if (title.equals(getMessage("gui.missions"))) {
            event.setCancelled(true);

            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                Player player = (Player) event.getWhoClicked();
                int page = currentPages.getOrDefault(player.getEntityId(), 1);
                openBattlePassGUI(player, page);
            }
        }
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    private static class PlayerData {
        UUID uuid;
        int xp = 0;
        int level = 1;
        int lastNotification = 0;
        int totalLevels = 0;
        boolean hasPremium = false;
        Map<String, Integer> missionProgress = new HashMap<>();
        Set<Integer> claimedFreeRewards = new HashSet<>();
        Set<Integer> claimedPremiumRewards = new HashSet<>();

        PlayerData(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static class Mission {
        String name;
        String type;
        String target;
        int required;
        int xpReward;

        Mission(String name, String type, String target, int required, int xpReward) {
            this.name = name;
            this.type = type;
            this.target = target;
            this.required = required;
            this.xpReward = xpReward;
        }
    }

    private static class MissionTemplate {
        String nameFormat;
        String type;
        String target;
        int minRequired;
        int maxRequired;
        int minXP;
        int maxXP;

        MissionTemplate(String nameFormat, String type, String target, int minRequired, int maxRequired, int minXP, int maxXP) {
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
        int level;
        Material material;
        int amount;
        boolean isFree;
        String command;
        String displayName;

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