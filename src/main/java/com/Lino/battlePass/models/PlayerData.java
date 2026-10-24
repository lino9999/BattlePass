package com.Lino.battlePass.models;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {
    public final UUID uuid;
    public int xp = 0;
    public int level = 1;
    public int lastNotification = 0;
    public int totalLevels = 0;
    public boolean hasPremium = false;
    public long lastDailyReward = 0;
    public int battleCoins = 0;
    public boolean excludeFromTop = false;
    public final Map<String, Integer> missionProgress = new ConcurrentHashMap<>();
    public final Set<Integer> claimedFreeRewards = ConcurrentHashMap.newKeySet();
    public final Set<Integer> claimedPremiumRewards = ConcurrentHashMap.newKeySet();
    private long progressRevision;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    /** Runtime reset/session boundary for synchronous XP hooks; deliberately not persisted or copied. */
    public long getProgressRevision() { return progressRevision; }

    public void invalidateProgress() { progressRevision++; }

    /** Credits only the amount representable by the persisted integer balance. */
    public int addBattleCoins(int amount) {
        int current = Math.max(0, battleCoins);
        int credited = Math.min(Math.max(0, amount), Integer.MAX_VALUE - current);
        battleCoins = current + credited;
        return credited;
    }

    public PlayerData copy() {
        PlayerData snapshot = new PlayerData(this.uuid);
        snapshot.xp = this.xp;
        snapshot.level = this.level;
        snapshot.lastNotification = this.lastNotification;
        snapshot.totalLevels = this.totalLevels;
        snapshot.hasPremium = this.hasPremium;
        snapshot.lastDailyReward = this.lastDailyReward;
        snapshot.battleCoins = this.battleCoins;
        snapshot.excludeFromTop = this.excludeFromTop;

        snapshot.missionProgress.putAll(this.missionProgress);
        snapshot.claimedFreeRewards.addAll(this.claimedFreeRewards);
        snapshot.claimedPremiumRewards.addAll(this.claimedPremiumRewards);

        return snapshot;
    }
}
