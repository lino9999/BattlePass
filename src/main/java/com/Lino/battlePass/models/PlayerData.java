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
    public final Map<String, Integer> missionProgress = new ConcurrentHashMap<>();
    public final Set<Integer> claimedFreeRewards = ConcurrentHashMap.newKeySet();
    public final Set<Integer> claimedPremiumRewards = ConcurrentHashMap.newKeySet();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }
}