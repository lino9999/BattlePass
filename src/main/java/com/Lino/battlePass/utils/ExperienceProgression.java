package com.Lino.battlePass.utils;

import com.Lino.battlePass.models.PlayerData;

/** Applies XP using a wide accumulator while preserving feedback for each gained level. */
public final class ExperienceProgression {
    public static final int MAX_FEEDBACK_LEVELS = 100;
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

    /** Presentation-only callback: preserve ordinary per-level feedback, summarize extreme grants at the final state. */
    public static long addWithLimitedFeedback(PlayerData data, int amount, int xpPerLevel, int maxLevel, Runnable feedback) {
        long remaining = (long) Math.max(0, data.xp) + Math.max(0, amount);
        int cost = Math.max(1, xpPerLevel);
        long gained = Math.min(remaining / cost, Math.max(0L, (long) maxLevel - data.level));
        if (gained <= MAX_FEEDBACK_LEVELS) return add(data, amount, xpPerLevel, maxLevel, feedback);
        data.level = (int) ((long) data.level + gained);
        data.totalLevels = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, data.totalLevels) + gained);
        data.xp = (int) Math.min(Integer.MAX_VALUE, remaining - gained * cost);
        feedback.run();
        return gained;
    }
}
