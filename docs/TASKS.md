# Periodic work and reward notifications

Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Registration, owned handle and disable ordering | `BattlePass.completeStartup`, `onDisable`, `periodicTask` |
| Periodic phase order, guards and cleanup clock | `tasks/BattlePassTask` |
| Reward notification state and presentation | `tasks/RewardNotifications` |
| Elapsed play time and reset boundaries | `listeners/PlayTimeTracker`, [RESET_WORKFLOW.md](RESET_WORKFLOW.md) |
| Independent periodic coin delivery | [COINS.md](COINS.md) |

The default Bukkit schedule remains a 6000-tick initial delay and 1200-tick period. Work stays on the server thread: save players, check season reset, check daily reset when progress is unpaused, notify rewards, sample play time, then perform due cleanup. Recheck activity between phases, players and cleanup components. A task captures its player/mission/reward managers; replacement managers, stopped/disabled plugins and terminal shutdown invalidate it. An in-stack guard prevents nested runs and releases in finally even after a top-level failure.

BattlePass owns the task and closes/cancels its own handle before the explicit final play-time flush on disable. The public constructor remains usable; an unscheduled manual task can also close safely. Old task instances remain closed after re-enable. Closing this periodic task must not close the connection elapsed-time tracker or suppress the existing final flush/multiplier/save ordering.

Pending resets suppress notifications, while active periodic time sampling continues to accumulate eligible elapsed time for the cycle coordinator. Both notification and play-time loops use online-player snapshots and skip offline players, so a callback disconnecting another player cannot invalidate iteration. A per-player notification/time failure logs and allows other players/phases to continue; system-level save/reset failures retain their existing propagation.

RewardNotifications binds each attempt to current cached PlayerData and its runtime progress revision. Count/reformat work is followed by ownership checks. Preserve ordinary positive-difference messages and decreasing-count updates; normalize negative saved markers to zero before calculating a difference. Publish lastNotification and mark dirty before sending, preventing nested notifications or a save/disconnect callback from observing an uncommitted marker. Failed sends restore/mark the previous normalized marker only while the same player/data/revision/counter is still owned, allowing a later retry. A sound failure keeps the already sent marker. Old continuations cannot overwrite reset/replacement state or play further feedback after invalidation. The existing message key, sound/volume/pitch and persisted column remain unchanged.

Cleanup retains its five-minute interval using System.nanoTime instead of wall-clock timestamps, with an internal clock constructor for deterministic verification. Compare elapsed subtraction so ordinary nanoTime wraparound works. A failed cleanup remains due for retry; reentry cannot execute another cleanup inside the current tick. GuiManager's public cleanup method remains a compatibility no-op.

Verification on 2026-10-24 passed 77 tests (41 existing plus 36 temporary). Cases cover phase ordering, reentry/retry, stop/reset/cache replacement, notification publication/rollback and failures, counter limits, changing online collections, player-isolated time errors, exact/recurring/wrapped cleanup deadlines, cancellation and real onDisable/final-flush ordering. Bukkit/scheduling/presentation are mocked; no live server was used. Temporary tests and generated artifacts are removed before delivery.
