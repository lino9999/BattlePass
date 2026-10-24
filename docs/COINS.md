# Coin distribution: edit map and invariants

Read this for periodic leaderboard coins. Shop purchases and item-token redemption still use cached `PlayerData` and dirty marking; do not replace their logic with the distribution path.

| Responsibility | Entry point |
| --- | --- |
| Restore one managed timer; preserve deadlines on reschedule/stop | `BattlePass.restoreCoinsDistributionTask`, `startCoinsDistributionTask`, lifecycle `stopping` flag |
| Due/pending/cancelled guards, ranking, notifications | `tasks/CoinsDistributionTask` |
| Drain snapshots, invalidate old loads, defer new loads/saves during commit | `managers/PlayerDataManager.prepareCoinsDistribution` / `finishCoinsDistribution` |
| Queued transaction boundary and deadline conflict | `DatabaseManager.commitCoinsDistribution`, `CoinsDeadlineChangedException` |
| Locked balances, capped credits, atomic deadline update | `CoinDistributionRepository` |
| Standalone deadline save/load and connection-level writer | `SeasonDataRepository` |

The task flushes cached player changes before reading the leaderboard asynchronously. It retains the due deadline until delivery succeeds and allows only one pending attempt. Cancellation, a changed local deadline, disabled shop or paused mission reset invalidate/postpone a pending attempt. A ranking read error leaves the due time intact for retry; failure to read the saved deadline at startup stops startup rather than inventing a fresh countdown.

The reload command also preserves the existing timer when a deadline read fails. Do not map a failed read to null: null represents an absent deadline and can create a new countdown. Reload revisions/manager identity discard stale callbacks, and feedback returns to the main thread. A later task-application failure is reported separately without claiming the previous task survived; see `RESET_WORKFLOW.md`.

On the Bukkit thread, preflight drains dirty snapshots again, verifies success, advances the player-cache generation, invalidates earlier loads and defers uncached loads/new snapshots. It does **not** discard cached player state. The task then waits for the short queued database commit; never hold the player-cache monitor across that wait. The commit uses one connection and a batch update for at most the leaderboard's ten wallets. Changing this phase to asynchronous work also requires coordinating spendable cached balances, intervening player saves, shutdown and reset paths.

`CoinDistributionRepository` locks the singleton calendar/deadline row and selected wallet rows (`FOR UPDATE` on MySQL). It checks the expected persisted deadline, reads current balances, caps credits at integer capacity, updates wallets and advances the deadline within one `DatabaseTransaction`. A conflict returns the persisted deadline without applying credits or poisoning the write queue. SQL failure rolls everything back and follows the existing critical-write fence/disable path. Transactional MySQL tables are required; this is not general cache coherence across multiple plugin/server instances.

`DatabaseManager.commitCoinsDistribution` takes a mutable request/result map; after success its values contain actual credited amounts. Only after commit does the task apply those amounts to existing cache entries and mark them dirty, publish its next deadline and notify players. Offline players are already persisted by the transaction: never write their old leaderboard balance back as an absolute balance. The existing deadline-save hook remains after commit with the same already-persisted value for compatibility. Deferred loads then resume against the committed data. Always release preflight in `finally`, but only if this attempt acquired it.

Validation evidence is in CODE_AUDIT. New regression tests must remain temporary and be deleted, including generated output, before delivery; existing tracked tests remain unchanged.
