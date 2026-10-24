# XP progression, multiplier events and mission actionbars

Read this guide for large XP grants, level notifications or mission actionbar timers. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| XP/level arithmetic, normal vs summarized feedback | `utils/ExperienceProgression` |
| Mission XP event/multiplier and dirty state | `managers/MissionProgressTracker` |
| Administrative XP | `commands/PlayerAdminCommands.checkLevelUp` |
| Level-boost items and XP event | `listeners/CustomItemsListener.handleLevelBoostUse` |
| Daily reward XP event and claim timestamp | `listeners/GuiClickListener.handleDailyRewardClaim` |
| Daily reward rolling window shared by menu, claim, timer and placeholder | `utils/DailyRewardCooldown`, `MissionResetHandler.getTimeUntilDailyReward` |
| Actionbar task ownership, duration and cleanup | `managers/MissionActionbars` |
| Multiplier-event preparation, timing, bossbar and shutdown | `managers/XPEventManager` |
| Multiplier-event commands / voucher consumption | `commands/XPEventCommands`, `listeners/CustomItemsListener.handleXPEventUse` |

All four production XP paths use `ExperienceProgression.addWithLimitedFeedback`. Preserve the wide accumulator until levels are deducted: nonnegative stored XP plus nonnegative grant, cost at least one, gained levels limited by remaining level capacity. Remaining XP and lifetime levels saturate at the persisted integer limit. The gained-level return is long, allowing correct differences even for unusual negative stored levels.

Up to `MAX_FEEDBACK_LEVELS` (100) gained levels, use the existing per-level sequence, including the intermediate XP/level/lifetime state seen by feedback. Above that threshold, apply the same final arithmetic in constant time and invoke the presentation callback once at the final level. This prevents millions of notifications/sounds/reward scans. All reward eligibility still follows the actual final level; no claim/reward sets, currencies or mission progress are changed by the arithmetic. Existing level-up/new-reward translations remain in use. The final callback runs after state is set and is not overwritten afterwards.

The older public `ExperienceProgression.add` retains arbitrary per-level callback semantics for integrations. Its work is intentionally proportional to gained levels; do not use that API for unbounded internal grants. The limited-feedback API is for presentation callbacks, not business logic that must run separately for every level. Do not merge or remove the existing XP events: mission, level-boost and daily-reward event cancellation/amount handling still occurs before progression.

Daily rewards use a rolling 86,400,000 ms window, independent of mission rotation. DailyRewardCooldown supplies both eligibility and nonnegative remaining milliseconds; saturate differences that exceed long range. Ancient timestamps remain eligible, future timestamps retain their cooldown, and exactly 24 hours is available. Menu lore, placeholder availability, the claim gate and timer formatting all use this helper. Preserve milliseconds until formatting rather than converting the stored timestamp to whole epoch seconds; the final subsecond must not display available early. The saved `lastDailyReward` value/column is unchanged.

Daily claim handling owns an in-stack UUID guard before calling the public XP event and releases it in finally. Recheck stopping/connection, current cached PlayerData identity and cooldown after the hook: reentrant claims, cache replacement/reset, disconnect or an externally moved timestamp cannot award stale/duplicate XP. Cancellation retains the existing contract of zero XP with the daily cooldown consumed; positive event modifications are respected. Timestamp/progression mutation is marked dirty in finally even if level-feedback presentation throws. Early rejection leaves data untouched. This is server-thread claim coordination, not a cross-server database lock or a general transaction around arbitrary UI callbacks.

`MissionActionbars` is owned by MissionProgressTracker; the tracker retains its public progress/reset/cleanup entry points. Each active bar maps a normalized mission-name key to its actual task object. Replacement/expiry/quit/reset/shutdown stops only the task that owns that entry. A stopped callback does nothing, including if the scheduler has already queued it. Remove ownership and mark stopped before cancellation or sending a clear message, since packet/chat callbacks can create another bar. Drop empty player maps; redundant historical timestamp/task-ID sets are no longer needed.

Progress repeats every five ticks for `duration * 4` messages, followed by the clear tick; completion repeats every twenty ticks for `duration` messages, then clears. Use long repeat/count arithmetic and clamp durations to at least one. Offline players and stopped/disabled plugins stop their existing bars and do not create new ones. Keep the existing actionbar-to-chat fallback and prefix. Mission persistence keys remain unchanged by UI key normalization.

Multiplier events change on the server thread. `XPEventManager` prepares a hidden bossbar, its player membership and countdown task before publishing a `RunningEvent`. An immutable timing/multiplier snapshot is published through a volatile owner so asynchronous placeholder reads do not touch bossbars or mix timing from different events. Preparation/scheduling/visibility failure cleans only the candidate and returns false while preserving the previous event. Nested starts are rejected; an explicit stop, shutdown or expiration during preparation invalidates the candidate.

Each countdown captures its event and may render/retire only while still owning the active entry. Remove ownership before cancellation, bossbar removal or expiry broadcasts. Stale/stopped callbacks do nothing; natural expiry announces once, while manual stop, shutdown and rendering failures do not announce a natural end. Cleanup attempts task cancellation, player removal and hiding independently, logging failures. Normal countdown period (20 ticks), progress, title and time formatting remain; color changes are sent only when the threshold color changes and recover after clock adjustments.

`stopEvent()` remains reusable. `BattlePass.onDisable` flushes final play-time credits first, then calls terminal `shutdown()`: an old retained manager cannot start events after re-enable. Distinguish refusing new work from the multiplier's logical validity. The stopping flag blocks starts/rendering/player additions, but the still-active multiplier must remain available to final play-time grants until shutdown explicitly closes the event. Checking the plugin stopping flag in multiplier getters would lose those final multiplied credits.

An XP-event voucher consumes one item from its actual hand only after `startEvent` succeeds. Failed starts retain the item and do not announce activation. Commands distinguish invalid input from valid requests that could not start via `messages.event.start-failed`. Missing message defaults are supplied by MessageManager without rewriting translations. Duration parsing is unchanged and checked for overflow. Multiplier parsing allows a number with one optional trailing `x` (case-insensitive); it must not strip embedded/repeated `x` characters into a valid number.

See `CODE_AUDIT.md` for temporary-test evidence. No live Minecraft server or real sound/packet delivery was used for these checks.
