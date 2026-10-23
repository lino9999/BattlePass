package com.Lino.battlePass.utils;

import com.Lino.battlePass.models.PlayerData;

/** Applies XP using a wide accumulator while preserving feedback for each gained level. */
public final class ExperienceProgression {
    private ExperienceProgression() {
    }

    public static int add(PlayerData data, int amount, int xpPerLevel, int maxLevel, Runnable onLevelUp) {
        long remaining = (long) Math.max(0, data.xp) + Math.max(0, amount);
        int cost = Math.max(1, xpPerLevel);
        int levels = 0;
        while (remaining >= cost && data.level < maxLevel) {
            remaining -= cost;
            data.xp = (int) Math.min(Integer.MAX_VALUE, remaining);
            data.level++;
            data.totalLevels = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, data.totalLevels) + 1);
            levels++;
            onLevelUp.run();
        }
        data.xp = (int) Math.min(Integer.MAX_VALUE, remaining);
        return levels;
    }
}
