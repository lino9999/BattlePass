# XP progression and mission actionbars

Read this guide for large XP grants, level notifications or mission actionbar timers. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| XP/level arithmetic, normal vs summarized feedback | `utils/ExperienceProgression` |
| Mission XP event/multiplier and dirty state | `managers/MissionProgressTracker` |
| Administrative XP | `commands/PlayerAdminCommands.checkLevelUp` |
| Level-boost items and XP event | `listeners/CustomItemsListener.handleLevelBoostUse` |
| Daily reward XP event and claim timestamp | `listeners/GuiClickListener.handleDailyRewardClaim` |
| Actionbar task ownership, duration and cleanup | `managers/MissionActionbars` |

All four production XP paths use `ExperienceProgression.addWithLimitedFeedback`. Preserve the wide accumulator until levels are deducted: nonnegative stored XP plus nonnegative grant, cost at least one, gained levels limited by remaining level capacity. Remaining XP and lifetime levels saturate at the persisted integer limit. The gained-level return is long, allowing correct differences even for unusual negative stored levels.

Up to `MAX_FEEDBACK_LEVELS` (100) gained levels, use the existing per-level sequence, including the intermediate XP/level/lifetime state seen by feedback. Above that threshold, apply the same final arithmetic in constant time and invoke the presentation callback once at the final level. This prevents millions of notifications/sounds/reward scans. All reward eligibility still follows the actual final level; no claim/reward sets, currencies or mission progress are changed by the arithmetic. Existing level-up/new-reward translations remain in use. The final callback runs after state is set and is not overwritten afterwards.

The older public `ExperienceProgression.add` retains arbitrary per-level callback semantics for integrations. Its work is intentionally proportional to gained levels; do not use that API for unbounded internal grants. The limited-feedback API is for presentation callbacks, not business logic that must run separately for every level. Do not merge or remove the existing XP events: mission, level-boost and daily-reward event cancellation/amount handling still occurs before progression.

`MissionActionbars` is owned by MissionProgressTracker; the tracker retains its public progress/reset/cleanup entry points. Each active bar maps a normalized mission-name key to its actual task object. Replacement/expiry/quit/reset/shutdown stops only the task that owns that entry. A stopped callback does nothing, including if the scheduler has already queued it. Remove ownership and mark stopped before cancellation or sending a clear message, since packet/chat callbacks can create another bar. Drop empty player maps; redundant historical timestamp/task-ID sets are no longer needed.

Progress repeats every five ticks for `duration * 4` messages, followed by the clear tick; completion repeats every twenty ticks for `duration` messages, then clears. Use long repeat/count arithmetic and clamp durations to at least one. Offline players and stopped/disabled plugins stop their existing bars and do not create new ones. Keep the existing actionbar-to-chat fallback and prefix. Mission persistence keys remain unchanged by UI key normalization.

See `CODE_AUDIT.md` for temporary-test evidence. No live Minecraft server or real sound/packet delivery was used for this change.
