package com.Lino.battlePass.utils;

/** The rolling 24-hour daily reward window, using saturated elapsed/remaining milliseconds. */
public final class DailyRewardCooldown {
    public static final long DAY_MILLIS = 86_400_000L;
    private DailyRewardCooldown() {}

    public static boolean isAvailable(long lastClaimed, long now) { return remainingMillis(lastClaimed, now) == 0; }

    public static long remainingMillis(long lastClaimed, long now) {
        if (now >= lastClaimed) {
            try {
                long elapsed = Math.subtractExact(now, lastClaimed);
                return elapsed >= DAY_MILLIS ? 0 : DAY_MILLIS - elapsed;
            } catch (ArithmeticException ancient) {
                return 0;
            }
        }
        try { return Math.addExact(Math.subtractExact(lastClaimed, now), DAY_MILLIS); }
        catch (ArithmeticException distantFuture) { return Long.MAX_VALUE; }
    }
}
