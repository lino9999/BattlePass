package com.Lino.battlePass.placeholders;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BattlePassExpansion extends PlaceholderExpansion {

    private final BattlePass plugin;

    public BattlePassExpansion(BattlePass plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "battlepass";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        if (data == null) {
            return "Loading...";
        }

        switch (identifier.toLowerCase()) {
            case "level":
                return String.valueOf(data.level);

            case "xp":
                return String.valueOf(data.xp);

            case "xp_needed":
                return String.valueOf(plugin.getConfigManager().getXpPerLevel());

            case "xp_progress":
                return String.valueOf(data.xp) + "/" + plugin.getConfigManager().getXpPerLevel();

            case "xp_percentage":
                int percentage = (data.xp * 100) / plugin.getConfigManager().getXpPerLevel();
                return String.valueOf(percentage);

            case "total_levels":
                return String.valueOf(data.totalLevels);

            case "premium":
                return data.hasPremium ? "Yes" : "No";

            case "premium_status":
                return data.hasPremium ? "Active" : "Inactive";

            case "coins":
            case "battlecoins":
                return String.valueOf(data.battleCoins);

            case "available_rewards":
                if (player.isOnline()) {
                    Player onlinePlayer = player.getPlayer();
                    return String.valueOf(plugin.getRewardManager().countAvailableRewards(onlinePlayer, data));
                }
                return "0";

            case "season_time":
            case "season_remaining":
                return plugin.getMissionManager().getTimeUntilSeasonEnd();

            case "missions_time":
            case "missions_reset":
                return plugin.getMissionManager().getTimeUntilReset();

            case "daily_reward_time":
                return plugin.getMissionManager().getTimeUntilDailyReward(data.lastDailyReward);

            case "daily_reward_available":
                long now = System.currentTimeMillis();
                return (now - data.lastDailyReward >= 24 * 60 * 60 * 1000) ? "Yes" : "No";

            case "rank":
            case "leaderboard_rank":
                return getPlayerRank(uuid);

            case "coins_distribution_time":
                if (plugin.getCoinsDistributionTask() != null) {
                    return plugin.getCoinsDistributionTask().getTimeUntilNextDistribution();
                }
                return "Unknown";

            case "completed_missions":
                return String.valueOf(getCompletedMissionsCount(data));

            case "total_missions":
                return String.valueOf(plugin.getMissionManager().getDailyMissions().size());

            case "missions_progress":
                int completed = getCompletedMissionsCount(data);
                int total = plugin.getMissionManager().getDailyMissions().size();
                return completed + "/" + total;
        }

        if (identifier.startsWith("mission_")) {
            return getMissionPlaceholder(data, identifier.substring(8));
        }

        if (identifier.startsWith("top_")) {
            return getTopPlayerPlaceholder(identifier.substring(4));
        }

        return null;
    }

    private String getPlayerRank(UUID uuid) {
        CompletableFuture<List<PlayerData>> future = plugin.getDatabaseManager().getTop10Players();
        List<PlayerData> topPlayers = future.join();

        for (int i = 0; i < topPlayers.size(); i++) {
            if (topPlayers.get(i).uuid.equals(uuid)) {
                return String.valueOf(i + 1);
            }
        }

        return "Unranked";
    }

    private int getCompletedMissionsCount(PlayerData data) {
        return plugin.getMissionManager().getCompletedMissionsCount(data);
    }

    private String getMissionPlaceholder(PlayerData data, String missionIdentifier) {
        List<Mission> missions = plugin.getMissionManager().getDailyMissions();

        try {
            int missionIndex = Integer.parseInt(missionIdentifier.replace("progress_", "").replace("name_", "").replace("status_", "")) - 1;

            if (missionIndex >= 0 && missionIndex < missions.size()) {
                Mission mission = missions.get(missionIndex);

                // FIX: Added .hashCode() to match MissionProgressTracker
                String key = mission.type + "_" + mission.target + "_" + mission.required + "_" + mission.name.hashCode();

                int progress = data.missionProgress.getOrDefault(key, 0);

                if (missionIdentifier.startsWith("progress_")) {
                    return progress + "/" + mission.required;
                } else if (missionIdentifier.startsWith("name_")) {
                    return mission.name;
                } else if (missionIdentifier.startsWith("status_")) {
                    return progress >= mission.required ? "Completed" : "In Progress";
                }
            }
        } catch (NumberFormatException ignored) {}

        return "";
    }

    private String getTopPlayerPlaceholder(String identifier) {
        try {
            String[] parts = identifier.split("_");
            int position = Integer.parseInt(parts[0]);

            if (position < 1 || position > 10) {
                return "";
            }

            CompletableFuture<List<PlayerData>> future = plugin.getDatabaseManager().getTop10Players();
            List<PlayerData> topPlayers = future.join();

            if (position > topPlayers.size()) {
                return "Empty";
            }

            PlayerData topPlayer = topPlayers.get(position - 1);

            if (parts.length > 1) {
                switch (parts[1].toLowerCase()) {
                    case "name":
                        return Bukkit.getOfflinePlayer(topPlayer.uuid).getName();
                    case "level":
                        return String.valueOf(topPlayer.level);
                    case "xp":
                        return String.valueOf(topPlayer.xp);
                    case "coins":
                        return String.valueOf(topPlayer.battleCoins);
                    case "totallevels":
                        return String.valueOf(topPlayer.totalLevels);
                }
            }

            return Bukkit.getOfflinePlayer(topPlayer.uuid).getName();

        } catch (NumberFormatException ignored) {}

        return "";
    }
}