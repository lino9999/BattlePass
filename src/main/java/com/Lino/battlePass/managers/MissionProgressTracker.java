package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.events.BattlePassXPGainEvent;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.utils.ExperienceProgression;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionProgressTracker {

    private final BattlePass plugin;
    private final MissionActionbars actionbars;
    private final Map<UUID, Set<String>> playerCompletedMissions = new ConcurrentHashMap<>();

    public MissionProgressTracker(BattlePass plugin) {
        this.plugin = plugin;
        this.actionbars = new MissionActionbars(plugin);
    }

    public void trackProgress(Player player, String type, String target, int amount, List<Mission> dailyMissions) {
        trackProgress(player, type, Collections.singletonList(target), amount, dailyMissions);
    }

    public void trackProgress(Player player, String type, Collection<String> targets, int amount, List<Mission> dailyMissions) {
        if (amount <= 0) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || dailyMissions.isEmpty() || targets.isEmpty()) return;

        UUID playerUUID = player.getUniqueId();
        Set<String> completedKeys = playerCompletedMissions.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet());

        boolean changed = false;
        MessageManager messageManager = plugin.getMessageManager();

        for (Mission mission : dailyMissions) {
            if (!mission.type.equals(type)) continue;

            boolean matches = false;
            for (String actionTarget : targets) {
                if (mission.isValidTarget(actionTarget)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;

            String missionKey = generateMissionKey(mission);

            if (completedKeys.contains(missionKey)) {
                continue;
            }

            int currentProgress = Math.max(0, data.missionProgress.getOrDefault(missionKey, 0));

            if (currentProgress >= mission.required) {
                completedKeys.add(missionKey);
                continue;
            }

            int newProgress = (int) Math.min((long) currentProgress + amount, mission.required);
            newProgress = Math.max(0, newProgress);
            data.missionProgress.put(missionKey, newProgress);
            changed = true;

            if (newProgress >= mission.required && currentProgress < mission.required) {
                completedKeys.add(missionKey);

                int eventMultiplier = plugin.getXpEventManager().getMultiplier();
                int xpToAdd = (int) Math.min(Integer.MAX_VALUE, (long) mission.xpReward * eventMultiplier);

                BattlePassXPGainEvent xpGainEvent = new BattlePassXPGainEvent(player, BattlePassXPGainEvent.XPSource.MISSION, xpToAdd);
                Bukkit.getPluginManager().callEvent(xpGainEvent);
                xpToAdd = xpGainEvent.isCancelled() ? 0 : Math.max(0, xpGainEvent.getAmount());

                if (xpToAdd > 0) {
                    checkLevelUp(player, data, xpToAdd);
                }

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

    public void resetProgress() {
        playerCompletedMissions.clear();
        actionbars.clearAll();
        for (PlayerData data : plugin.getPlayerDataManager().getPlayerCache().values()) data.missionProgress.clear();
    }

    private void checkLevelUp(Player player, PlayerData data, int amount) {
        MessageManager messageManager = plugin.getMessageManager();
        int xpPerLevel = plugin.getConfigManager().getXpPerLevel();
        int maxLevel = plugin.getRewardManager().getMaxLevel();

        long levels = ExperienceProgression.addWithLimitedFeedback(data, amount, xpPerLevel, maxLevel, () -> {
            player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.level-up",
                    "%level%", String.valueOf(data.level)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int available = plugin.getRewardManager().countAvailableRewards(data);
            if (available > 0) {
                player.sendMessage(messageManager.getPrefix() + messageManager.getMessage("messages.new-rewards"));
            }
        });

        if (levels > 0) {
            plugin.getPlayerDataManager().markForSave(player.getUniqueId());
        }
    }

    private void showProgressActionbar(Player player, String missionName, int current, int required) {
        actionbars.progress(player, missionName, current, required);
    }

    private void showCompletedActionbar(Player player, String missionName) {
        actionbars.completed(player, missionName);
    }

    public void clearPlayerActionbars(UUID uuid) {
        actionbars.clearPlayer(uuid);
        playerCompletedMissions.remove(uuid);
    }

    public void shutdown() {
        actionbars.clearAll();
        playerCompletedMissions.clear();
    }

    public int getCompletedMissionsCount(PlayerData data, List<Mission> missions) {
        int completed = 0;
        for (Mission mission : missions) {
            String key = generateMissionKey(mission);
            int progress = Math.max(0, data.missionProgress.getOrDefault(key, 0));
            if (progress >= mission.required) {
                completed++;
            }
        }
        return completed;
    }
}
