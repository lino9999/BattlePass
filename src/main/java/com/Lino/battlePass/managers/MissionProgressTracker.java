package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionProgressTracker {

    private final BattlePass plugin;
    private final Map<UUID, Map<String, Long>> lastActionbarUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> actionbarTasks = new ConcurrentHashMap<>();
    private final Set<Integer> scheduledTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> playerCompletedMissions = new ConcurrentHashMap<>();

    public MissionProgressTracker(BattlePass plugin) {
        this.plugin = plugin;
    }

    public void trackProgress(Player player, String type, String target, int amount, List<Mission> dailyMissions) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || dailyMissions.isEmpty()) return;

        UUID playerUUID = player.getUniqueId();
        Set<String> completedKeys = playerCompletedMissions.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet());

        boolean changed = false;
        MessageManager messageManager = plugin.getMessageManager();

        for (Mission mission : dailyMissions) {
            if (!mission.type.equals(type)) continue;

            if (!mission.target.equals("ANY") && !mission.target.equals(target)) continue;

            String missionKey = generateMissionKey(mission);

            if (completedKeys.contains(missionKey)) {
                continue;
            }

            int currentProgress = data.missionProgress.getOrDefault(missionKey, 0);

            if (currentProgress >= mission.required) {
                completedKeys.add(missionKey);
                continue;
            }

            int newProgress = Math.min(currentProgress + amount, mission.required);
            data.missionProgress.put(missionKey, newProgress);
            changed = true;

            if (newProgress >= mission.required && currentProgress < mission.required) {
                completedKeys.add(missionKey);

                int eventMultiplier = plugin.getXpEventManager().getMultiplier();
                int xpToAdd = mission.xpReward * eventMultiplier;
                data.xp += xpToAdd;
                checkLevelUp(player, data);

                String xpText = String.valueOf(xpToAdd);
                if (eventMultiplier > 1) {
                    xpText = xpToAdd + " (" + eventMultiplier + "x)";
                }

                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.mission.completed",
                        "%mission%", mission.name,
                        "%reward_xp%", xpText));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                showCompletedActionbar(player, mission.name);
            } else {
                showProgressActionbar(player, mission.name, newProgress, mission.required);
            }
        }

        if (changed) {
            plugin.getPlayerDataManager().markForSave(player.getUniqueId());
        }
    }

    private String generateMissionKey(Mission mission) {
        return mission.type + "_" + mission.target + "_" + mission.required + "_" + mission.name.hashCode();
    }

    public void resetProgress(String currentMissionDate) {
        playerCompletedMissions.clear();
        lastActionbarUpdate.clear();

        for (Map.Entry<UUID, Map<String, Integer>> entry : actionbarTasks.entrySet()) {
            for (Integer taskId : entry.getValue().values()) {
                Bukkit.getScheduler().cancelTask(taskId);
                scheduledTaskIds.remove(taskId);
            }
        }
        actionbarTasks.clear();

        for (PlayerData data : plugin.getPlayerDataManager().getPlayerCache().values()) {
            data.missionProgress.clear();
        }
    }

    private void checkLevelUp(Player player, PlayerData data) {
        boolean leveled = false;
        MessageManager messageManager = plugin.getMessageManager();
        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
        int maxLevel = plugin.getRewardManager().getMaxLevel();

        while (data.xp >= xpPerLevel && data.level < maxLevel) {
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
            plugin.getPlayerDataManager().markForSave(player.getUniqueId());
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
                "%mission%", missionName,
                "%current%", String.valueOf(current),
                "%required%", String.valueOf(required));

        int taskId = new BukkitRunnable() {
            private int count = 0;

            @Override
            public void run() {
                if (count >= 40) {
                    sendActionBar(player, "");
                    Integer currentTaskId = playerTasks.remove(key);
                    if (currentTaskId != null) {
                        Bukkit.getScheduler().cancelTask(currentTaskId);
                        scheduledTaskIds.remove(currentTaskId);
                    }
                    return;
                }
                sendActionBar(player, progressMessage);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 5L).getTaskId();

        playerTasks.put(key, taskId);
        scheduledTaskIds.add(taskId);
    }

    private void showCompletedActionbar(Player player, String missionName) {
        UUID uuid = player.getUniqueId();

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

        MessageManager messageManager = plugin.getMessageManager();
        String completedMessage = messageManager.getMessage("messages.mission.actionbar-completed",
                "%mission%", missionName);

        int taskId = new BukkitRunnable() {
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
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

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
        playerCompletedMissions.remove(uuid);
    }

    public void shutdown() {
        scheduledTaskIds.forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId));
        scheduledTaskIds.clear();

        actionbarTasks.values().forEach(tasks ->
                tasks.values().forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId))
        );
        actionbarTasks.clear();
        lastActionbarUpdate.clear();
        playerCompletedMissions.clear();
    }

    public int getCompletedMissionsCount(PlayerData data, List<Mission> missions) {
        int completed = 0;
        for (Mission mission : missions) {
            String key = generateMissionKey(mission);
            int progress = data.missionProgress.getOrDefault(key, 0);
            if (progress >= mission.required) {
                completed++;
            }
        }
        return completed;
    }
}