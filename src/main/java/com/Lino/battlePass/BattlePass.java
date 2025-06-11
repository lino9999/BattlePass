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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BattlePass extends JavaPlugin implements Listener, CommandExecutor {

    private Connection connection;
    private final Map<UUID, PlayerData> playerCache = new HashMap<>();
    private final Map<Integer, Integer> currentPages = new HashMap<>();
    private List<Mission> dailyMissions = new ArrayList<>();
    private final List<Reward> freeRewards = new ArrayList<>();
    private final List<Reward> premiumRewards = new ArrayList<>();
    private FileConfiguration config;
    private LocalDateTime nextMissionReset;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getServer().getPluginManager().registerEvents(this, this);
        initDatabase();
        loadRewardsFromConfig();
        generateDailyMissions();
        calculateNextReset();

        getCommand("battlepass").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllPlayers();
                checkMissionReset();
            }
        }.runTaskTimer(this, 6000L, 1200L);

        getLogger().info("BattlePass enabled successfully!");
    }

    @Override
    public void onDisable() {
        saveAllPlayers();
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
                            "claimed_premium TEXT DEFAULT '')"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS missions (" +
                            "uuid TEXT," +
                            "mission TEXT," +
                            "progress INTEGER DEFAULT 0," +
                            "date TEXT," +
                            "PRIMARY KEY (uuid, mission, date))"
            );

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
                        "INSERT INTO players (uuid, xp, level, claimed_free, claimed_premium) VALUES (?, 0, 1, '', '')"
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
                    "UPDATE players SET xp = ?, level = ?, claimed_free = ?, claimed_premium = ? WHERE uuid = ?"
            );
            ps.setInt(1, data.xp);
            ps.setInt(2, data.level);
            ps.setString(3, String.join(",", data.claimedFreeRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setString(4, String.join(",", data.claimedPremiumRewards.stream().map(String::valueOf).toArray(String[]::new)));
            ps.setString(5, uuid.toString());
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
        loadPlayer(event.getPlayer().getUniqueId());
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
            player.sendMessage("§6§lLEVEL UP! §fYou are now level §e" + data.level);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
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
            } else {
                ItemStack empty = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
                ItemMeta emptyMeta = empty.getItemMeta();
                emptyMeta.setDisplayName(" ");
                empty.setItemMeta(emptyMeta);
                gui.setItem(9 + i, empty);
            }

            Reward free = freeRewards.stream()
                    .filter(r -> r.level == level)
                    .findFirst()
                    .orElse(null);

            if (free != null) {
                ItemStack freeItem = createRewardItem(free, data, true, false);
                gui.setItem(27 + i, freeItem);
            } else {
                ItemStack empty = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
                ItemMeta emptyMeta = empty.getItemMeta();
                emptyMeta.setDisplayName(" ");
                empty.setItemMeta(emptyMeta);
                gui.setItem(27 + i, empty);
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
        ItemStack item = new ItemStack(reward.material, reward.amount);
        ItemMeta meta = item.getItemMeta();

        Set<Integer> claimedSet = isPremium ? data.claimedPremiumRewards : data.claimedFreeRewards;

        if (data.level >= reward.level && hasAccess && !claimedSet.contains(reward.level)) {
            meta.setDisplayName("§a§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§a§lCLICK TO CLAIM!"
            ));
            item.setType(Material.LIME_STAINED_GLASS_PANE);
        } else if (claimedSet.contains(reward.level)) {
            meta.setDisplayName("§7§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§7§lALREADY CLAIMED"
            ));
        } else if (!hasAccess && isPremium) {
            meta.setDisplayName("§6§lLevel " + reward.level + " Premium Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§6§lPREMIUM ONLY"
            ));
            item.setType(Material.ORANGE_STAINED_GLASS_PANE);
        } else {
            meta.setDisplayName("§c§lLevel " + reward.level + " " + (isPremium ? "Premium" : "Free") + " Reward");
            meta.setLore(Arrays.asList(
                    "§7Reward: §f" + reward.amount + "x " + formatMaterial(reward.material),
                    "§7Required Level: §e" + reward.level,
                    "",
                    "§c§lLOCKED (Level " + reward.level + " Required)"
            ));
            item.setType(Material.RED_STAINED_GLASS_PANE);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.startsWith("§6§lBattle Pass")) {
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