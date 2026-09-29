package com.Lino.battlePass.managers;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonTimingTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 1, 31, 18, 30);

    @Test
    void durationIsMeasuredFromOriginalStartAcrossRestarts() {
        SeasonTiming original = SeasonTiming.startingAt(start, "DURATION", 90);
        SeasonTiming restored = SeasonTiming.restore(start.plusDays(20), original.end(),
                original.start(), "DURATION", 90);
        assertEquals(start.plusDays(90), restored.end());
        assertEquals(start.plusDays(90), SeasonTiming.deadline(start, "DURATION", 90));
    }

    @Test
    void configurationChangeUsesOriginalStart() {
        SeasonTiming updated = SeasonTiming.restore(start.plusDays(20), start.plusDays(30),
                start, "DURATION", 150);
        assertEquals(start.plusDays(150), updated.end());
    }

    @Test
    void legacyDurationMigratesOnceFromNow() {
        LocalDateTime now = start.plusDays(20);
        SeasonTiming migrated = SeasonTiming.restore(now, start.plusDays(30), null, "DURATION", 150);
        assertEquals(now.plusDays(150), migrated.end());
        assertEquals(migrated.end(), SeasonTiming.restore(now.plusDays(1), migrated.end(),
                migrated.start(), "DURATION", 150).end());
    }

    @Test
    void monthStartHandlesLastDayOfLongMonth() {
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0),
                SeasonTiming.deadline(start, "MONTH_START", 90));
    }
}
