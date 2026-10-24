package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.Reward;
import com.Lino.battlePass.utils.RewardConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class RewardManager {

    private final BattlePass plugin;
    private final ConfigManager configManager;
    private final RewardClaimHandler claimHandler;

    private final Map<Integer, List<Reward>> freeRewardsByLevel = new HashMap<>();
    private final Map<Integer, List<Reward>> premiumRewardsByLevel = new HashMap<>();

    private int maxLevel = 54;

    public RewardManager(BattlePass plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.claimHandler = new RewardClaimHandler(plugin, configManager);
    }

    public void loadRewards() {
        FileConfiguration freeConfig = configManager.getBattlePassFreeConfig();
        FileConfiguration premiumConfig = configManager.getBattlePassPremiumConfig();
        Map<Integer, List<Reward>> nextFree = RewardConfiguration.readRuntimeRewards(freeConfig, true, plugin.getLogger()::warning);
        Map<Integer, List<Reward>> nextPremium = RewardConfiguration.readRuntimeRewards(premiumConfig, false, plugin.getLogger()::warning);
        int nextMaxLevel = 54;
        for (int level : nextFree.keySet()) nextMaxLevel = Math.max(nextMaxLevel, level);
        for (int level : nextPremium.keySet()) nextMaxLevel = Math.max(nextMaxLevel, level);

        freeRewardsByLevel.clear();
        premiumRewardsByLevel.clear();
        nextFree.forEach((level, rewards) -> { if (!rewards.isEmpty()) freeRewardsByLevel.put(level, rewards); });
        nextPremium.forEach((level, rewards) -> { if (!rewards.isEmpty()) premiumRewardsByLevel.put(level, rewards); });
        maxLevel = nextMaxLevel;
    }

    public void claimRewards(Player player, PlayerData data, List<Reward> rewards,
                             int level, boolean isPremium) {
        claimHandler.claimRewards(player, data, rewards, level, isPremium);
    }

    public int countAvailableRewards(PlayerData data) {
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

    public Map<Integer, List<Reward>> getFreeRewardsByLevel() {
        return freeRewardsByLevel;
    }

    public Map<Integer, List<Reward>> getPremiumRewardsByLevel() {
        return premiumRewardsByLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getMaxPage() {
        return (int) Math.ceil(getMaxLevel() / 9.0);
    }

    public List<EditableReward> getEditableRewards(int level, boolean isPremium) {
        FileConfiguration config = isPremium ? configManager.getBattlePassPremiumConfig() : configManager.getBattlePassFreeConfig();
        return RewardConfiguration.readEditableRewards(config, level);
    }
}
