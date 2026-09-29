package com.Lino.battlePass.managers;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** The persisted anchor lets configuration changes adjust the current season only once. */
public record SeasonTiming(LocalDateTime start, LocalDateTime end, String resetType, int duration) {

    public static SeasonTiming startingAt(LocalDateTime start, String resetType, int duration) {
        return new SeasonTiming(start, deadline(start, resetType, duration), resetType, duration);
    }

    public static SeasonTiming restore(LocalDateTime now, LocalDateTime savedEnd,
                                       LocalDateTime savedStart, String configuredType, int configuredDuration) {
        if (savedEnd == null) {
            return startingAt(now, configuredType, configuredDuration);
        }
        if (savedStart == null) {
            // Old databases contain only the deadline. The previous start and duration
            // cannot be recovered; anchor this season once at migration time.
            return startingAt(now, configuredType, configuredDuration);
        }
        return startingAt(savedStart, configuredType, configuredDuration);
    }

    public static LocalDateTime deadline(LocalDateTime start, String resetType, int duration) {
        if ("MONTH_START".equalsIgnoreCase(resetType)) {
            return start.with(TemporalAdjusters.firstDayOfMonth()).plusMonths(1)
                    .toLocalDate().atStartOfDay();
        }
        return start.plusDays(duration);
    }
}
