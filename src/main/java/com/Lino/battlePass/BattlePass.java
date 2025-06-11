package com.Lino.battlePass;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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
    private List<Mission> dailyMissions = new ArrayList<>();
    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private FileConfiguration config;
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;
    private int seasonDuration = 30;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

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
            }
        }.runTaskTimer(this, 6000L, 1200L);

        getLogger().info("BattlePass enabled successfully!");
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("battlepass.admin")) {
            reloadConfig();
            config = getConfig();
            loadRewardsFromConfig();
            player.sendMessage("§aBattlePass configuration reloaded!");
            return true;
        }

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
                            "total_levels INTEGER DEFAULT 0)"
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
            player.sendMessage("§6§l[SEASON RESET] §eThe Battle Pass season has ended! A new season begins now!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("UPDATE players SET xp = 0, level = 1, claimed_free = '', claimed_premium = ''");
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

        return String.format("%dd %dh", days, hours);
    }

    private void checkRewardNotifications() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = loadPlayer(player.getUniqueId());
            int availableRewards = countAvailableRewards(player, data);

            if (availableRewards > data.lastNotification) {
                player.sendMessage("§6§l[BATTLE PASS] §eYou have §f" + (availableRewards - data.lastNotification) + " §enew rewards to claim!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                data.lastNotification = availableRewards;
            }
        }
    }

    private int countAvailableRewards(Player player, PlayerData data) {
        int count = 0;
        boolean hasPremium = player.hasPermission("battlepass.premium");

        for (Reward reward : freeRewards) {
            if (data.level >= reward.level && !data.claimedFreeRewards.contains(reward.level)) {
                count++;
            }
        }

        if (hasPremium) {
            for (Reward reward : premiumRewards) {
                if (data.level >= reward.level && !data.claimedPremiumRewards.contains(reward.level)) {
                    count++;
                }
            }
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
                String material = config.getString(freePath + ".material", "DIRT");
                int amount = config.getInt(freePath + ".amount", 1);
                int requiredXP = (i - 1) * 200;

                try {
                    Material mat = Material.valueOf(material.toUpperCase());
                    freeRewards.add(new Reward(i, requiredXP, mat, amount, true));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid material for free level " + i + ": " + material);
                }
            }

            if (config.contains(premiumPath)) {
                String material = config.getString(premiumPath + ".material", "DIAMOND");
                int amount = config.getInt(premiumPath + ".amount", 1);
                int requiredXP = (i - 1) * 200;

                try {
                    Material mat = Material.valueOf(material.toUpperCase());
                    premiumRewards.add(new Reward(i, requiredXP, mat, amount, false));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid material for premium level " + i + ": " + material);
                }
            }
        }
    }

    private void generateDailyMissions() {
        dailyMissions.clear();
        List<MissionTemplate> templates = Arrays.asList(
                new MissionTemplate("Kill %d Mobs", MissionType.KILL_MOBS, 10, 30, 100, 200),
                new MissionTemplate("Break %d Blocks", MissionType.BREAK_BLOCKS, 50, 200, 150, 250),
                new MissionTemplate("Kill %d Players", MissionType.KILL_PLAYERS, 3, 10, 200, 400),
                new MissionTemplate("Mine %d Diamonds", MissionType.MINE_DIAMONDS, 10, 30, 300, 500),
                new MissionTemplate("Kill %d Endermen", MissionType.KILL_ENDERMEN, 3, 10, 250, 400),
                new MissionTemplate("Kill %d Zombies", MissionType.KILL_ZOMBIES, 20, 50, 150, 250),
                new MissionTemplate("Kill %d Skeletons", MissionType.KILL_SKELETONS, 15, 40, 150, 250),
                new MissionTemplate("Mine %d Iron Ore", MissionType.MINE_IRON, 30, 100, 100, 200),
                new MissionTemplate("Mine %d Gold Ore", MissionType.MINE_GOLD, 20, 50, 200, 300),
                new MissionTemplate("Kill %d Creepers", MissionType.KILL_CREEPERS, 10, 25, 200, 300)
        );

        Collections.shuffle(templates);

        for (int i = 0; i < 5 && i < templates.size(); i++) {
            MissionTemplate template = templates.get(i);
            int required = ThreadLocalRandom.current().nextInt(template.minRequired, template.maxRequired + 1);
            int xpReward = ThreadLocalRandom.current().nextInt(template.minXP, template.maxXP + 1);
            String name = String.format(template.nameFormat, required);

            dailyMissions.add(new Mission(name, template.type, required, xpReward));
        }
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
                player.sendMessage("§6§lDaily missions have been reset!");
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

        return String.format("%dh %dm", hours, minutes);
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
                        "INSERT INTO players (uuid, xp, level, claimed_free, claimed_premium, last_notification, total_levels) VALUES (?, 0, 1, '', '', 0, 0)"
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
                    "UPDATE players SET xp = ?, level = ?, claimed_free = ?, claimed_premium = ?, last_notification = ?, total_levels = ? WHERE uuid = ?"
            );
            ps.setInt(1, data.xp);
            ps.setInt(2, data.level);
            ps.setString(3, String.join(",", data.claimedFreeRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setString(4, String.join(",", data.claimedPremiumRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setInt(5, data.lastNotification);
            ps.setInt(6, data.totalLevels);
            ps.setString(7, uuid.toString());
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

        new BukkitRunnable() {
            @Override
            public void run() {
                int available = countAvailableRewards(player, data);
                if (available > 0) {
                    player.sendMessage("§6§l[BATTLE PASS] §eYou have §f" + available + " §erewards available to claim!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            }
        }.runTaskLater(this, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayer(event.getPlayer().getUniqueId());
        playerCache.remove(event.getPlayer().getUniqueId());
        currentPages.remove(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();

            if (event.getEntity() instanceof Player) {
                progressMission(killer, MissionType.KILL_PLAYERS, 1);
            } else {
                progressMission(killer, MissionType.KILL_MOBS, 1);

                String entityType = event.getEntityType().name();
                switch (entityType) {
                    case "ENDERMAN":
                        progressMission(killer, MissionType.KILL_ENDERMEN, 1);
                        break;
                    case "ZOMBIE":
                        progressMission(killer, MissionType.KILL_ZOMBIES, 1);
                        break;
                    case "SKELETON":
                        progressMission(killer, MissionType.KILL_SKELETONS, 1);
                        break;
                    case "CREEPER":
                        progressMission(killer, MissionType.KILL_CREEPERS, 1);
                        break;
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        progressMission(player, MissionType.BREAK_BLOCKS, 1);

        Material blockType = event.getBlock().getType();
        switch (blockType) {
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                progressMission(player, MissionType.MINE_DIAMONDS, 1);
                break;
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                progressMission(player, MissionType.MINE_IRON, 1);
                break;
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                progressMission(player, MissionType.MINE_GOLD, 1);
                break;
        }
    }

    private void progressMission(Player player, MissionType type, int amount) {
        PlayerData data = loadPlayer(player.getUniqueId());

        for (Mission mission : dailyMissions) {
            if (mission.type == type) {
                String key = mission.name.toLowerCase().replace(" ", "_");
                int current = data.missionProgress.getOrDefault(key, 0);

                if (current < mission.required) {
                    current = Math.min(current + amount, mission.required);
                    data.missionProgress.put(key, current);

                    if (current >= mission.required) {
                        data.xp += mission.xpReward;
                        checkLevelUp(player, data);
                        player.sendMessage("§a§lMission Completed: §f" + mission.name + " §7(+" + mission.xpReward + " XP)");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private void checkLevelUp(Player player, PlayerData data) {
        int requiredXP = data.level * 200;
        while (data.xp >= requiredXP && data.level < 54) {
            data.xp -= requiredXP;
            data.level++;
            data.totalLevels++;
            player.sendMessage("§6§lLEVEL UP! §fYou are now level §e" + data.level);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage("§e§lNEW REWARDS! §fYou have new rewards available to claim!");
            }

            requiredXP = data.level * 200;
        }
    }

    private void openBattlePassGUI(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lBattle Pass §7- Page " + page);
        PlayerData data = loadPlayer(player.getUniqueId());
        boolean hasPremium = player.hasPermission("battlepass.premium");

        currentPages.put(player.getEntityId(), page);

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lYour Progress");
        infoMeta.setLore(Arrays.asList(
                "§7Level: §e" + data.level + "§7/§e54",
                "§7XP: §e" + data.xp + "§7/§e" + (data.level * 200),
                "",
                "§7Premium Pass: " + (hasPremium ? "§a§lACTIVE" : "§c§lINACTIVE"),
                "§7Season Ends: §e" + getTimeUntilSeasonEnd(),
                "",
                "§7Complete missions to earn XP",
                "§7and unlock rewards!"
        ));
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
        sepMeta.setDisplayName(" ");
        separator.setItemMeta(sepMeta);
        for (int i = 18; i < 27; i++) {
            gui.setItem(i, separator);
        }

        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§e§lPrevious Page");
            prevMeta.setLore(Arrays.asList("§7Click to go to page " + (page - 1)));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (endLevel < 54) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§e§lNext Page");
            nextMeta.setLore(Arrays.asList("§7Click to go to page " + (page + 1)));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack missions = new ItemStack(Material.BOOK);
        ItemMeta missionsMeta = missions.getItemMeta();
        missionsMeta.setDisplayName("§b§lDaily Missions");
        missionsMeta.setLore(Arrays.asList(
                "§7Complete daily missions",
                "§7to earn bonus XP!",
                "",
                "§7Resets in: §e" + getTimeUntilReset(),
                "",
                "§e§lCLICK TO VIEW"
        ));
        missions.setItemMeta(missionsMeta);
        gui.setItem(49, missions);

        ItemStack leaderboard = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta leaderMeta = leaderboard.getItemMeta();
        leaderMeta.setDisplayName("§6§lLeaderboard");
        leaderMeta.setLore(Arrays.asList(
                "§7View the top 10 players",
                "§7with highest battle pass levels!",
                "",
                "§e§lCLICK TO VIEW"
        ));
        leaderboard.setItemMeta(leaderMeta);
        gui.setItem(48, leaderboard);

        player.openInventory(gui);
    }

    private void openLeaderboardGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lBattle Pass Leaderboard");

        ItemStack title = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§6§lTop 10 Players");
        titleMeta.setLore(Arrays.asList(
                "§7Season ends in: §e" + getTimeUntilSeasonEnd(),
                "",
                "§7Compete for the top spots!"
        ));
        title.setItemMeta(titleMeta);
        gui.setItem(4, title);

        List<PlayerData> topPlayers = getTop10Players();
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

        for (int i = 0; i < topPlayers.size() && i < 10; i++) {
            PlayerData topPlayer = topPlayers.get(i);
            String playerName = Bukkit.getOfflinePlayer(topPlayer.uuid).getName();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(topPlayer.uuid));

            String rank = "§7#" + (i + 1);
            if (i == 0) rank = "§6§l#1";
            else if (i == 1) rank = "§7§l#2";
            else if (i == 2) rank = "§c§l#3";

            skullMeta.setDisplayName(rank + " §f" + playerName);
            skullMeta.setLore(Arrays.asList(
                    "§7Level: §e" + topPlayer.level,
                    "§7Total Levels: §e" + topPlayer.totalLevels,
                    "§7XP: §e" + topPlayer.xp,
                    "",
                    topPlayer.uuid.equals(player.getUniqueId()) ? "§a§lTHIS IS YOU!" : "§7Keep grinding to beat them!"
            ));
            skull.setItemMeta(skullMeta);
            gui.setItem(slots[i], skull);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§lBack");
        backMeta.setLore(Arrays.asList("§7Return to Battle Pass"));
        back.setItemMeta(backMeta);
        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private void openMissionsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§b§lDaily Missions");
        PlayerData data = loadPlayer(player.getUniqueId());

        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta timerMeta = timer.getItemMeta();
        timerMeta.setDisplayName("§e§lTime Until Reset");
        timerMeta.setLore(Arrays.asList(
                "§7Missions reset in:",
                "§f" + getTimeUntilReset(),
                "",
                "§7Complete missions before",
                "§7they reset to earn XP!"
        ));
        timer.setItemMeta(timerMeta);
        gui.setItem(4, timer);

        int slot = 19;
        for (Mission mission : dailyMissions) {
            if (slot > 25) slot = 28;
            if (slot > 34) break;

            String key = mission.name.toLowerCase().replace(" ", "_");
            int progress = data.missionProgress.getOrDefault(key, 0);
            boolean completed = progress >= mission.required;

            ItemStack missionItem = new ItemStack(completed ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta missionMeta = missionItem.getItemMeta();
            missionMeta.setDisplayName((completed ? "§a§l" : "§e§l") + mission.name);
            missionMeta.setLore(Arrays.asList(
                    "§7Progress: §f" + progress + "§7/§f" + mission.required,
                    "§7Reward: §e" + mission.xpReward + " XP",
                    "",
                    "§7Resets in: §e" + getTimeUntilReset(),
                    "",
                    completed ? "§a§lCOMPLETED" : "§e§lIN PROGRESS"
            ));
            missionItem.setItemMeta(missionMeta);
            gui.setItem(slot++, missionItem);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§lBack");
        backMeta.setLore(Arrays.asList("§7Return to Battle Pass"));
        back.setItemMeta(backMeta);
        gui.setItem(49, back);

        player.openInventory(gui);
    }

    private ItemStack createRewardItem(Reward reward, PlayerData data, boolean hasAccess, boolean isPremium) {
        Set<Integer> claimedSet = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;
        ItemStack item;
        ItemMeta meta;

        if (data.level >= reward.level && hasAccess && !claimedSet.contains(reward.level)) {
            item = new ItemStack(Material.CHEST_MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName("§a§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§7Season ends in: §c" + getTimeUntilSeasonEnd(),
                    "",
                    "§a§lCLICK TO CLAIM!"
            ));
        } else if (claimedSet.contains(reward.level)) {
            item = new ItemStack(reward.material, reward.amount);
            meta = item.getItemMeta();
            meta.setDisplayName("§7§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§7Season ends in: §c" + getTimeUntilSeasonEnd(),
                    "",
                    "§7§lALREADY CLAIMED"
            ));
        } else if (!hasAccess && isPremium) {
            item = new ItemStack(Material.MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName("§6§lLevel " + reward.level + " Premium Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§7Season ends in: §c" + getTimeUntilSeasonEnd(),
                    "",
                    "§6§lPREMIUM ONLY"
            ));
        } else {
            item = new ItemStack(Material.MINECART, 1);
            meta = item.getItemMeta();
            meta.setDisplayName("§c§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§7Season ends in: §c" + getTimeUntilSeasonEnd(),
                    "",
                    "§c§lLOCKED (Level " + reward.level + " Required)"
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.startsWith("§6§lBattle Pass") && !title.contains("Leaderboard")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            if (clicked == null || !clicked.hasItemMeta()) return;

            if (clicked.getType() == Material.ARROW) {
                String displayName = clicked.getItemMeta().getDisplayName();
                int currentPage = currentPages.getOrDefault(player.getEntityId(), 1);

                if (displayName.contains("Previous")) {
                    openBattlePassGUI(player, currentPage - 1);
                } else if (displayName.contains("Next")) {
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

                    if (player.hasPermission("battlepass.premium")) {
                        Reward reward = premiumRewards.stream()
                                .filter(r -> r.level == level)
                                .findFirst()
                                .orElse(null);

                        if (reward != null && data.level >= reward.level && !data.claimedPremiumRewards.contains(reward.level)) {
                            data.claimedPremiumRewards.add(reward.level);
                            player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                            player.sendMessage("§6§lPremium Reward Claimed! §fYou received " + reward.amount + "x " + formatMaterial(reward.material));
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                            openBattlePassGUI(player, currentPage);
                        }
                    }
                } else if (slot >= 27 && slot <= 35) {
                    int index = slot - 27;
                    int level = startLevel + index;

                    Reward reward = freeRewards.stream()
                            .filter(r -> r.level == level)
                            .findFirst()
                            .orElse(null);

                    if (reward != null && data.level >= reward.level && !data.claimedFreeRewards.contains(reward.level)) {
                        data.claimedFreeRewards.add(reward.level);
                        player.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                        player.sendMessage("§a§lFree Reward Claimed! §fYou received " + reward.amount + "x " + formatMaterial(reward.material));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        openBattlePassGUI(player, currentPage);
                    }
                }
            }
        } else if (title.equals("§6§lBattle Pass Leaderboard")) {
            event.setCancelled(true);

            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                Player player = (Player) event.getWhoClicked();
                int page = currentPages.getOrDefault(player.getEntityId(), 1);
                openBattlePassGUI(player, page);
            }
        } else if (title.equals("§b§lDaily Missions")) {
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
        Map<String, Integer> missionProgress = new HashMap<>();
        Set<Integer> claimedFreeRewards = new HashSet<>();
        Set<Integer> claimedPremiumRewards = new HashSet<>();

        PlayerData(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static class Mission {
        String name;
        MissionType type;
        int required;
        int xpReward;

        Mission(String name, MissionType type, int required, int xpReward) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.xpReward = xpReward;
        }
    }

    private static class MissionTemplate {
        String nameFormat;
        MissionType type;
        int minRequired;
        int maxRequired;
        int minXP;
        int maxXP;

        MissionTemplate(String nameFormat, MissionType type, int minRequired, int maxRequired, int minXP, int maxXP) {
            this.nameFormat = nameFormat;
            this.type = type;
            this.minRequired = minRequired;
            this.maxRequired = maxRequired;
            this.minXP = minXP;
            this.maxXP = maxXP;
        }
    }

    private static class Reward {
        int level;
        int requiredXP;
        Material material;
        int amount;
        boolean isFree;

        Reward(int level, int requiredXP, Material material, int amount, boolean isFree) {
            this.level = level;
            this.requiredXP = requiredXP;
            this.material = material;
            this.amount = amount;
            this.isFree = isFree;
        }
    }

    private enum MissionType {
        KILL_MOBS, BREAK_BLOCKS, KILL_PLAYERS, MINE_DIAMONDS, KILL_ENDERMEN,
        KILL_ZOMBIES, KILL_SKELETONS, MINE_IRON, MINE_GOLD, KILL_CREEPERS
    }
}