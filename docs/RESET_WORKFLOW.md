# Reset requests, command feedback and play time

Use this guide for reset commands/API results, delayed admin actions or play time crossing a mission/season reset. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Reset request admission, completion and persistence failure | `managers/MissionManager` |
| Completion values | `models/ResetResult` |
| Delayed warning, permission recheck, feedback and reload reads | `commands/SeasonAdminCommands` |
| Legacy handler delegation and season calendar preparation | `managers/MissionResetHandler` |
| Cycle timing hooks | `listeners/EventManager`, `PlayerConnectionListener`, `PlayTimeTracker` |

Request resets on the server thread through `forceResetSeasonAsync`, `resetSeasonAsync` or `forceResetMissionsAsync`. Async describes completion, not permission to mutate Bukkit state on a worker thread. The returned future completes with COMPLETED only after the transaction and main-thread application/finalizer succeed. Rejected requests return BUSY, COOLDOWN, STOPPED or NOT_READY; preflight/database/apply failures complete exceptionally. Existing void entry points remain wrappers. The older public `MissionResetHandler` methods delegate to this coordinator rather than performing separate partial/cache-only resets. Legacy season futures fail for a rejected request rather than pretending completion.

The preparation gate rejects reentrant requests while old time/snapshots are flushed, without pausing the old mission's final credits. A failed preflight releases that gate and does not consume the forced-season cooldown. A pending reset owns its result future; acknowledge/clear pending state before completing callbacks so a callback cannot erase a newer operation. Persistence failure completes active requests exceptionally before requesting plugin disable. Shutdown can complete the pending committed finalizer directly.

Season commands retain the 40-tick warning and allow one queued warning per command router. Recheck permission, player connection and manager identity after the delay. Daily/season completion text and administrator broadcasts depend on COMPLETED, never on submitting a void method or merely expiring the warning. Rejections/errors use packaged message defaults. Result callbacks marshal feedback to the server thread and ignore stopped/replaced managers; disconnected senders receive no message. An authorized operation already committed still has its ordinary broadcast if the sender later disconnects.

Reload reports configuration failure without throwing through the command. A failed saved-coin-deadline read keeps the existing timer and reports that failure instead of treating it as an absent deadline. Timer-application failures use a separate message without promising the old task survived. A revision/manager guard prevents an older reload read from overwriting a newer one or restarting a stopped/replaced instance. Existing translations are preserved; `MessageManager` supplies missing packaged defaults in memory.

Before clearing player data, `EventManager.beginPlayTimeCycle` flushes whole eligible old-cycle minutes and stages the old remainder under an opaque token. Elapsed eligible time after this boundary accumulates separately while a reset is pending, with the existing disabled-world and data-ready rules. The finalizer samples pending time while progress is still paused, discards the old-cycle remainder, and releases only the new-cycle accumulator. Old partial/pending time must not complete a minute for unrelated replacement missions.

If preparation fails before database submission, abort merges the staged old remainder back into the unchanged cycle; earned whole minutes were already acknowledged before callbacks. Disconnect/reinitialize clears both sides of that player's old session, and stale tokens cannot affect a newer transition. Season calendars use the captured preparation boundary. Ordinary periodic fractions still survive within a cycle. All timing remains session-local: the protocol does not persist elapsed time across plugin/server failure or disconnected sessions.

See `SEASONS.md` for SQL/file recovery, `COINS.md` for wallet/deadline commits and `CODE_AUDIT.md` for temporary regression evidence.
