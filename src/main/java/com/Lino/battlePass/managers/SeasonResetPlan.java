package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.Mission;
import java.time.LocalDateTime;
import java.util.*;

/** Immutable input captured before a season-reset job can outlive a configuration reload. */
record SeasonResetPlan(String id, int rotationTarget, boolean resetCoins, LocalDateTime start,
                       LocalDateTime end, LocalDateTime cycleStart, LocalDateTime nextReset,
                       String missionDate, List<Mission> missions) {
    SeasonResetPlan {
        if (id == null || id.isBlank() || rotationTarget < 0) throw new IllegalArgumentException("Invalid season transition");
        Objects.requireNonNull(start); Objects.requireNonNull(end); Objects.requireNonNull(cycleStart);
        Objects.requireNonNull(nextReset); Objects.requireNonNull(missionDate);
        missions = missions.stream().map(m -> new Mission(m.name, m.type, m.target,
                new ArrayList<>(m.additionalTargets), m.required, m.xpReward)).toList();
    }
}
