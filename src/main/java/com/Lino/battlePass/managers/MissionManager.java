package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.MissionTemplate;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MissionManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerDataManager playerDataManager;

    private volatile List<Mission> dailyMissions = Collections.synchronizedList(new ArrayList<>());
    private LocalDateTime nextMissionReset;
    private LocalDateTime seasonEndDate;

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

                if (LocalDateTime.now().isAfter(seasonEndDate)) {
                    Bukkit.getScheduler().runTask(plugin, this::resetSeason);
                }
            } else {
                seasonEndDate = LocalDateTime.now().plusDays(configManager.getSeasonDuration());
                saveSeasonData();
            }
        });

        databaseManager.loadDailyMissions().thenAccept(missions -> {
            if (missions.isEmpty() || nextMissionReset == null || LocalDateTime.now().isAfter(nextMissionReset)) {
                generateDailyMissions();
                if (nextMissionReset == null || LocalDateTime.now().isAfter(nextMissionReset)) {
                    calculateNextReset();
                }
                saveDailyMissions();
            } else {
                dailyMissions = new ArrayList<>(missions);
            }
        });
    }

    private void generateDailyMissions() {
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
        nextMissionReset = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
    }

    public void checkMissionReset() {
        if (LocalDateTime.now().isAfter(nextMissionReset)) {
            generateDailyMissions();
            calculateNextReset();
            saveDailyMissions();

            MessageManager messageManager = plugin.getMessageManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.reset"));
            }

            for (PlayerData data : playerDataManager.getPlayerCache().values()) {
                data.missionProgress.clear();
            }
        }
    }

    public void checkSeasonReset() {
        if (LocalDateTime.now().isAfter(seasonEndDate)) {
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
        generateDailyMissions();
        calculateNextReset();
        saveSeasonData();
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
                        }
                    }
                }
            }
        }

        if (changed) {
            playerDataManager.markForSave(player.getUniqueId());
        }
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
        databaseManager.saveSeasonData(seasonEndDate, configManager.getSeasonDuration(), nextMissionReset);
    }

    private void saveDailyMissions() {
        databaseManager.saveDailyMissions(dailyMissions);
    }

    public void shutdown() {
        saveSeasonData();
        saveDailyMissions();
    }

    public String getTimeUntilReset() {
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
}