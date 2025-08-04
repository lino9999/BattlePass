package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.MissionTemplate;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.tasks.CoinsDistributionTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MissionManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerDataManager playerDataManager;

    private volatile List<Mission> dailyMissions = Collections.synchronizedList(new ArrayList<>());
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;
    private String currentMissionDate;

    private final Map<UUID, Map<String, Long>> lastActionbarUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> actionbarTasks = new ConcurrentHashMap<>();
    private final Set<Integer> scheduledTaskIds = ConcurrentHashMap.newKeySet();

    public MissionManager(BattlePass plugin, ConfigManager configManager, DatabaseManager databaseManager, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerDataManager = playerDataManager;
    }

    public void initialize() {
        databaseManager.loadSeasonData().thenAccept(data -> {
            if (data.containsKey("endDate")) {
                seasonEndDate = (LocalDateTime) data.get("endDate");
                if (data.containsKey("missionResetTime")) {
                    nextMissionReset = (LocalDateTime) data.get("missionResetTime");
                }
                if (data.containsKey("currentMissionDate")) {
                    currentMissionDate = (String) data.get("currentMissionDate");
                }

                if (LocalDateTime.now().isAfter(seasonEndDate)) {
                    Bukkit.getScheduler().runTask(plugin, this::resetSeason);
                    return;
                }
            } else {
                seasonEndDate = LocalDateTime.now().plusDays(configManager.getSeasonDuration());
                currentMissionDate = LocalDateTime.now().toLocalDate().toString();
                calculateNextReset();
                saveSeasonData();
            }

            loadMissionsAfterSeasonData();
        });
    }

    private void loadMissionsAfterSeasonData() {
        databaseManager.loadDailyMissions().thenAccept(missions -> {
            LocalDateTime now = LocalDateTime.now();

            if (currentMissionDate == null) {
                currentMissionDate = now.toLocalDate().toString();
            }

            boolean needNewMissions = missions.isEmpty() ||
                    (nextMissionReset != null && now.isAfter(nextMissionReset));

            if (needNewMissions) {
                if (nextMissionReset != null && now.isAfter(nextMissionReset)) {
                    currentMissionDate = now.toLocalDate().toString();
                    databaseManager.clearOldMissionProgress(currentMissionDate);
                }

                generateDailyMissions();
                calculateNextReset();
                saveDailyMissions();
                saveSeasonData();
            } else {
                dailyMissions = new ArrayList<>(missions);

                if (nextMissionReset == null) {
                    calculateNextReset();
                    saveSeasonData();
                }
            }
        });
    }

    private void generateDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }

        ConfigurationSection pools = configManager.getMissionsConfig().getConfigurationSection("mission-pools");
        if (pools == null) {
            plugin.getLogger().warning("No mission pools found in missions.yml!");
            return;
        }

        List<Mission> newMissions = new ArrayList<>();
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
            plugin.getLogger().warning("No valid missions found in missions.yml!");
            return;
        }

        Collections.shuffle(templates);

        for (int i = 0; i < configManager.getDailyMissionsCount() && i < templates.size(); i++) {
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

        dailyMissions = new ArrayList<>(newMissions);
    }

    private String formatTarget(String target) {
        if (target.equals("ANY")) {
            return "";
        }
        return target.toLowerCase().replace("_", " ");
    }

    private void calculateNextReset() {
        LocalDateTime now = LocalDateTime.now();
        int hoursInterval = configManager.getMissionResetHours();
        nextMissionReset = now.plusHours(hoursInterval);
    }

    public void checkMissionReset() {
        if (nextMissionReset != null && LocalDateTime.now().isAfter(nextMissionReset)) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();

            generateDailyMissions();
            calculateNextReset();
            saveDailyMissions();
            saveSeasonData();

            MessageManager messageManager = plugin.getMessageManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.reset"));
            }

            for (PlayerData data : playerDataManager.getPlayerCache().values()) {
                data.missionProgress.clear();
            }

            databaseManager.clearOldMissionProgress(currentMissionDate);

            lastActionbarUpdate.clear();
            actionbarTasks.clear();
        }
    }

    public void checkSeasonReset() {
        if (seasonEndDate != null && LocalDateTime.now().isAfter(seasonEndDate)) {
            resetSeason();
        }
    }

    private void resetSeason() {
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.season.reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        databaseManager.resetSeason();
        playerDataManager.clearCache();

        seasonEndDate = LocalDateTime.now().plusDays(configManager.getSeasonDuration());
        currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        generateDailyMissions();
        calculateNextReset();
        saveSeasonData();

        lastActionbarUpdate.clear();
        actionbarTasks.clear();
    }

    public void forceResetSeason() {
        MessageManager messageManager = plugin.getMessageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.season.forced-reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }

        playerDataManager.saveAllPlayers();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            databaseManager.resetSeason().thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    playerDataManager.clearCache();

                    seasonEndDate = LocalDateTime.now().plusDays(configManager.getSeasonDuration());
                    currentMissionDate = LocalDateTime.now().toLocalDate().toString();
                    generateDailyMissions();
                    calculateNextReset();
                    saveSeasonData();
                    saveDailyMissions();

                    lastActionbarUpdate.clear();
                    actionbarTasks.clear();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playerDataManager.loadPlayer(player.getUniqueId());
                    }

                    if (plugin.getCoinsDistributionTask() != null) {
                        plugin.getCoinsDistributionTask().cancel();
                        CoinsDistributionTask newTask = new CoinsDistributionTask(plugin);
                        newTask.runTaskTimer(plugin, 200L, 1200L);
                    }
                });
            });
        }, 20L);
    }

    public void forceResetMissions() {
        MessageManager messageManager = plugin.getMessageManager();

        currentMissionDate = LocalDateTime.now().toLocalDate().toString();

        generateDailyMissions();
        calculateNextReset();
        saveDailyMissions();
        saveSeasonData();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.forced-reset"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        for (PlayerData data : playerDataManager.getPlayerCache().values()) {
            data.missionProgress.clear();
        }

        databaseManager.clearOldMissionProgress(currentMissionDate);

        lastActionbarUpdate.clear();
        actionbarTasks.clear();
    }

    public void progressMission(Player player, String type, String target, int amount) {
        PlayerData data = playerDataManager.getPlayerData(player.getUniqueId());
        if (data == null || dailyMissions.isEmpty()) return;

        boolean changed = false;
        List<Mission> currentMissions = new ArrayList<>(dailyMissions);
        MessageManager messageManager = plugin.getMessageManager();

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

                            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.completed",
                                    "%mission%", mission.name,
                                    "%reward_xp%", String.valueOf(mission.xpReward)));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                            showCompletedActionbar(player, mission.name);
                        } else {
                            showProgressActionbar(player, mission.name, current, mission.required);
                        }
                    }
                }
            }
        }

        if (changed) {
            playerDataManager.markForSave(player.getUniqueId());
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            player.sendMessage("§7[§6Progress§7] " + message);
        }
    }

    private void showProgressActionbar(Player player, String missionName, int current, int required) {
        UUID uuid = player.getUniqueId();

        if (!lastActionbarUpdate.containsKey(uuid)) {
            lastActionbarUpdate.put(uuid, new ConcurrentHashMap<>());
        }
        if (!actionbarTasks.containsKey(uuid)) {
            actionbarTasks.put(uuid, new ConcurrentHashMap<>());
        }

        Map<String, Integer> playerTasks = actionbarTasks.get(uuid);
        String key = missionName.toLowerCase().replace(" ", "_");

        if (playerTasks.containsKey(key)) {
            int oldTaskId = playerTasks.get(key);
            Bukkit.getScheduler().cancelTask(oldTaskId);
            scheduledTaskIds.remove(oldTaskId);
        }

        lastActionbarUpdate.get(uuid).put(key, System.currentTimeMillis());

        MessageManager messageManager = plugin.getMessageManager();
        String progressMessage = messageManager.getMessage("messages.mission.actionbar-progress",
                "%current%", String.valueOf(current),
                "%required%", String.valueOf(required),
                "%mission%", missionName);

        sendActionBar(player, progressMessage);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Long lastUpdate = lastActionbarUpdate.getOrDefault(uuid, new HashMap<>()).get(key);
                if (lastUpdate != null && System.currentTimeMillis() - lastUpdate >= 29500) {
                    sendActionBar(player, "");
                    playerTasks.remove(key);
                    lastActionbarUpdate.get(uuid).remove(key);
                }
            }
        }, 600L).getTaskId();

        playerTasks.put(key, taskId);
        scheduledTaskIds.add(taskId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduledTaskIds.remove(taskId);
        }, 601L);
    }

    private void showCompletedActionbar(Player player, String missionName) {
        UUID uuid = player.getUniqueId();
        String key = missionName.toLowerCase().replace(" ", "_") + "_completed";

        if (!actionbarTasks.containsKey(uuid)) {
            actionbarTasks.put(uuid, new ConcurrentHashMap<>());
        }

        Map<String, Integer> playerTasks = actionbarTasks.get(uuid);

        if (playerTasks.containsKey(key)) {
            int oldTaskId = playerTasks.get(key);
            Bukkit.getScheduler().cancelTask(oldTaskId);
            scheduledTaskIds.remove(oldTaskId);
        }

        MessageManager messageManager = plugin.getMessageManager();
        String completedMessage = messageManager.getMessage("messages.mission.actionbar-completed",
                "%mission%", missionName);

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int count = 0;

            @Override
            public void run() {
                if (count >= 15) {
                    sendActionBar(player, "");
                    Integer currentTaskId = playerTasks.remove(key);
                    if (currentTaskId != null) {
                        Bukkit.getScheduler().cancelTask(currentTaskId);
                        scheduledTaskIds.remove(currentTaskId);
                    }
                    return;
                }
                sendActionBar(player, completedMessage);
                count++;
            }
        }, 0L, 20L).getTaskId();

        playerTasks.put(key, taskId);
        scheduledTaskIds.add(taskId);
    }

    public void clearPlayerActionbars(UUID uuid) {
        if (actionbarTasks.containsKey(uuid)) {
            actionbarTasks.get(uuid).values().forEach(taskId -> {
                Bukkit.getScheduler().cancelTask(taskId);
                scheduledTaskIds.remove(taskId);
            });
            actionbarTasks.remove(uuid);
        }
        lastActionbarUpdate.remove(uuid);
    }

    public String getTimeUntilDailyReward(long lastClaimed) {
        LocalDateTime lastClaimTime = LocalDateTime.ofEpochSecond(lastClaimed / 1000, 0, java.time.ZoneOffset.UTC);
        LocalDateTime nextAvailable = lastClaimTime.plusDays(1);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

        if (now.isAfter(nextAvailable)) {
            return plugin.getMessageManager().getMessage("time.available-now");
        }

        long hours = ChronoUnit.HOURS.between(now, nextAvailable);
        long minutes = ChronoUnit.MINUTES.between(now, nextAvailable) % 60;

        MessageManager messageManager = plugin.getMessageManager();
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");
        String minuteStr = minutes == 1 ? messageManager.getMessage("time.minute") : messageManager.getMessage("time.minutes");

        return messageManager.getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    private void checkLevelUp(Player player, PlayerData data) {
        boolean leveled = false;
        MessageManager messageManager = plugin.getMessageManager();
        int xpPerLevel = configManager.getXpPerLevel();

        while (data.xp >= xpPerLevel && data.level < 54) {
            data.xp -= xpPerLevel;
            data.level++;
            data.totalLevels++;
            leveled = true;

            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.level-up",
                    "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = plugin.getRewardManager().countAvailableRewards(player, data);
            if (available > 0) {
                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.new-rewards"));
            }
        }

        if (leveled) {
            playerDataManager.markForSave(player.getUniqueId());
        }
    }

    private void saveSeasonData() {
        databaseManager.saveSeasonData(seasonEndDate, configManager.getSeasonDuration(), nextMissionReset, currentMissionDate);
    }

    private void saveDailyMissions() {
        if (currentMissionDate == null) {
            currentMissionDate = LocalDateTime.now().toLocalDate().toString();
        }
        databaseManager.saveDailyMissions(dailyMissions, currentMissionDate);
    }

    public void shutdown() {
        saveSeasonData();
        saveDailyMissions();

        scheduledTaskIds.forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId));
        scheduledTaskIds.clear();

        actionbarTasks.values().forEach(tasks ->
                tasks.values().forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId))
        );
        actionbarTasks.clear();
        lastActionbarUpdate.clear();
    }

    public String getTimeUntilReset() {
        if (nextMissionReset == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        long hours = ChronoUnit.HOURS.between(now, nextMissionReset);
        long minutes = ChronoUnit.MINUTES.between(now, nextMissionReset) % 60;

        MessageManager messageManager = plugin.getMessageManager();
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");
        String minuteStr = minutes == 1 ? messageManager.getMessage("time.minute") : messageManager.getMessage("time.minutes");

        return messageManager.getMessage("time.hours-minutes", "%hours%", String.valueOf(hours),
                "%minutes%", String.valueOf(minutes));
    }

    public String getTimeUntilSeasonEnd() {
        if (seasonEndDate == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(now, seasonEndDate);
        long hours = ChronoUnit.HOURS.between(now, seasonEndDate) % 24;

        MessageManager messageManager = plugin.getMessageManager();
        String dayStr = days == 1 ? messageManager.getMessage("time.day") : messageManager.getMessage("time.days");
        String hourStr = hours == 1 ? messageManager.getMessage("time.hour") : messageManager.getMessage("time.hours");

        return messageManager.getMessage("time.days-hours", "%days%", String.valueOf(days),
                "%hours%", String.valueOf(hours));
    }

    public List<Mission> getDailyMissions() {
        return new ArrayList<>(dailyMissions);
    }

    public String getCurrentMissionDate() {
        return currentMissionDate;
    }

    public boolean isInitialized() {
        return currentMissionDate != null && !dailyMissions.isEmpty();
    }}