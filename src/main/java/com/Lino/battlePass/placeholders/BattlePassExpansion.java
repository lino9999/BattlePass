package com.Lino.battlePass.placeholders;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

public class BattlePassExpansion extends PlaceholderExpansion {

    private final BattlePass plugin;
    private final LeaderboardPlaceholders leaderboard;

    public BattlePassExpansion(BattlePass plugin) { this(plugin, System::nanoTime); }

    BattlePassExpansion(BattlePass plugin, LongSupplier clock) {
        this.plugin = plugin;
        leaderboard = new LeaderboardPlaceholders(plugin, clock);
    }

    public void shutdown() { leaderboard.close(); }

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
        if (identifier == null) return null;
        if (leaderboard.isClosed() || plugin.isStopping() || !plugin.isEnabled()) return "";
        identifier = identifier.toLowerCase(Locale.ROOT);
        if (identifier.startsWith("top_")) return leaderboard.top(identifier.substring(4));
        switch (identifier) {
            case "rank", "leaderboard_rank":
                return player == null ? "" : leaderboard.rank(player.getUniqueId());
            case "season_time", "season_remaining":
                return plugin.getMissionManager().getTimeUntilSeasonEnd();
            case "missions_time", "missions_reset":
                return plugin.getMissionManager().getTimeUntilReset();
            case "coins_distribution_time":
                var distribution = plugin.getCoinsDistributionTask();
                return distribution == null ? "Unknown" : distribution.getTimeUntilNextDistribution();
            case "total_missions":
                return String.valueOf(plugin.getMissionManager().getDailyMissions().size());
            case "current_season":
                return String.valueOf(plugin.getSeasonRotationManager().getCurrentSeason());
            case "total_seasons":
                return String.valueOf(plugin.getSeasonRotationManager().getTotalSeasons());
            case "xp_event_active":
                return plugin.getXpEventManager().isEventActive() ? "Yes" : "No";
            case "xp_event_multiplier":
                return plugin.getXpEventManager().getMultiplier() + "x";
            case "xp_event_time", "xp_event_remaining":
                return plugin.getXpEventManager().getTimeRemaining();
        }
        if (identifier.startsWith("mission_name_")) return mission(null, identifier.substring(8));
        if (identifier.startsWith("mission_") && !identifier.startsWith("mission_progress_") && !identifier.startsWith("mission_status_")) return "";
        boolean personal = switch (identifier) {
            case "level", "xp", "xp_needed", "xp_progress", "xp_percentage", "total_levels", "premium",
                    "premium_status", "coins", "battlecoins", "available_rewards", "daily_reward_time",
                    "daily_reward_available", "completed_missions", "missions_progress" -> true;
            default -> identifier.startsWith("mission_progress_") || identifier.startsWith("mission_status_");
        };
        if (!personal) return null;
        if (player == null) return "";
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return "Loading...";
        return switch (identifier) {
            case "level" -> String.valueOf(data.level);
            case "xp" -> String.valueOf(data.xp);
            case "xp_needed" -> String.valueOf(plugin.getConfigManager().getXpPerLevel());
            case "xp_progress" -> data.xp + "/" + plugin.getConfigManager().getXpPerLevel();
            case "xp_percentage" -> String.valueOf((long) data.xp * 100 / Math.max(1, plugin.getConfigManager().getXpPerLevel()));
            case "total_levels" -> String.valueOf(data.totalLevels);
            case "premium" -> data.hasPremium ? "Yes" : "No";
            case "premium_status" -> data.hasPremium ? "Active" : "Inactive";
            case "coins", "battlecoins" -> String.valueOf(data.battleCoins);
            case "available_rewards" -> player.isOnline() ? String.valueOf(plugin.getRewardManager().countAvailableRewards(data)) : "0";
            case "daily_reward_time" -> plugin.getMissionManager().getTimeUntilDailyReward(data.lastDailyReward);
            case "daily_reward_available" -> System.currentTimeMillis() - data.lastDailyReward >= 86400000L ? "Yes" : "No";
            case "completed_missions" -> String.valueOf(plugin.getMissionManager().getCompletedMissionsCount(data));
            case "missions_progress" -> plugin.getMissionManager().getCompletedMissionsCount(data) + "/" + plugin.getMissionManager().getDailyMissions().size();
            default -> mission(data, identifier.substring(8));
        };
    }

    private String mission(PlayerData data, String identifier) {
        int separator = identifier.indexOf('_');
        if (separator < 0) return "";
        String field = identifier.substring(0, separator);
        if (!field.equals("name") && !field.equals("progress") && !field.equals("status")) return "";
        List<Mission> missions = plugin.getMissionManager().getDailyMissions();
        try {
            int position = Integer.parseInt(identifier.substring(separator + 1));
            if (position < 1 || position > missions.size()) return "";
            Mission mission = missions.get(position - 1);
            if (field.equals("name")) return mission.name;
            if (data == null) return "";
            String key = mission.type + "_" + mission.target + "_" + mission.required + "_" + mission.name.hashCode();
            int progress = Math.max(0, data.missionProgress.getOrDefault(key, 0));
            return field.equals("progress") ? progress + "/" + mission.required
                    : progress >= mission.required ? "Completed" : "In Progress";
        } catch (NumberFormatException invalid) {
            return "";
        }
    }
}
