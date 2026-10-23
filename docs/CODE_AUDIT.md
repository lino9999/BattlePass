# Code audit — started 2026-10-23

Status: in progress. This is a source review and regression-test record, not a claim that every possible server/plugin interaction has been proven safe. Keep the existing local deletion of `CHANGELOG.md` out of audit commits.

## Verified fixes: player persistence and menu input

- Player writes now acknowledge the exact dirty revision captured by the snapshot. Changes made while that write is pending remain dirty and are saved on the next pass.
- Autosave and quit no longer submit overlapping writes for the same player. Synchronous flush waits for a pending write, then saves any newer revision. Failed writes remain eligible for retry. Autosave visits dirty IDs rather than copying all cached players.
- Duplicate player loads are coalesced. Cache clearing invalidates pending loads, preventing an old load from restoring pre-reset data or replacing a newer loaded instance. Failed loads allow retries.
- Main menus and mission editors only activate buttons from the top inventory. Clicks in the player's own inventory stay cancelled where required but no longer invoke menu actions.
- Dragging into read-only main menus, mission editors and reward category menus is cancelled. Bottom-only drags and foreign inventories remain unaffected; the reward item editor keeps its existing item-copy workflow.

Validation: the initial 10 temporary regression checks reproduced six failures in the old code. After the fixes, all 52 tests passed (41 pre-existing plus 11 temporary tests; the drag test covers 12 combinations). Temporary test sources/classes/reports are removed before delivery as required by the owner.

## Verified fixes: failed flush and single-connection pools

- `clearCache(true)` retains the cache and reports failure if revisions remain unsaved after flushing. An explicit `clearCache(false)` still discards state intentionally.
- Season reset performs that flush before rotating configuration or changing deadlines. A failed flush postpones the reset and preserves player state for retry.
- Player reads/writes resolve the current mission date before borrowing their player connection. The date lookup uses its own lease, so MySQL pool size one no longer causes nested connection acquisition and timeouts.

Validation: four temporary boundary tests cover failed flush/retry, reset preflight and read/write connection acquisition order. All 45 tests passed together; MySQL lease behavior was verified through mocked JDBC/connection ownership, without a live MySQL server.

## Verified fixes: rewards, shop templates and XP event input

- Loaded item rewards retain their exact configuration path. Two items of the same material no longer both resolve to the first entry, and a single plain YAML item also goes through the metadata loader. Existing Reward constructors remain available.
- Reward claiming checks cached claims, level eligibility and premium ownership at the manager boundary. Direct/repeated calls cannot bypass those checks while a database save is pending. Reward messages retain the original delivered amount even if inventory insertion mutates the stack.
- Shop insertion receives a cloned item stack, keeping the configured template intact across repeated purchases.
- XP duration parsing accepts complete h/m/s sequences (case-insensitive, whitespace allowed), rejecting malformed/overflowing values without exceptions. API starts validate multiplier, duration and end-time overflow before touching a running event; the command handles a rejected start.
- Replacing an expired XP event also removes its bar/task, even when its scheduled expiration tick has not run yet.

Validation: 23 temporary tests cover distinct same-material rewards, metadata-loader selection, duplicate/premium claims, repeated purchases with a mutating inventory, duration formats/overflow, API rejection and expired-event cleanup. All 64 tests passed together; temporary tests are removed before delivery. Bukkit registries, inventory insertion and item deserialization were mocked; no live server was used.

## Verified fixes: menu materials, mission bounds and cache lock ordering

- Reward rows are handled before material-based action buttons. Available/locked reward icons may share materials with books, navigation arrows, gold, sunflowers or command blocks without becoming unrelated controls. Action/back buttons also require their intended slots; a shop item using BARRIER outside the back slot remains purchasable.
- Mission ranges use a long upper bound so `Integer.MAX_VALUE` remains a valid inclusive maximum. Weight totals/selection use long arithmetic. Positive progress increments use long arithmetic before clamping to the requirement, preventing wraparound to zero.
- The earlier cache-generation fix exposed a lock-order issue: a synchronous cache clear could hold the cache monitor while waiting for a write behind a load whose completion needed that monitor. The flush now runs outside the monitor; generation checking and clearing remain inside it. A controlled single-thread database executor reproduced the timeout before correction.

Validation: 12 temporary tests cover six reward-icon material collisions, a purchasable barrier shop item, maximum mission bounds, overflowing progress, controlled load/write lock ordering, failed-flush retention and stale-load invalidation. All 53 tests passed together. Temporary tests are removed before delivery.

## Verified fixes: experience and currency arithmetic

- `ExperienceProgression` centralizes XP accumulation for missions, admin grants, daily rewards and elixirs. A long accumulator preserves the full sum through level conversion; callback feedback still runs once per gained level. Stored XP and lifetime level counts cannot wrap negative at the integer limit.
- Mission XP multipliers/elixir stack multiplication widen before conversion to the int-valued XP event API. Admin XP removal uses a long total and direct level/remainder calculation rather than an overflow-prone total and a level-by-level subtraction loop.
- `PlayerData.addBattleCoins` caps the persisted balance at `Integer.MAX_VALUE` and returns the actual credited amount. Commands/distribution report that amount. Coin tokens consume only the amount that fits; a full wallet keeps the tokens. Negative configured coin distributions are clamped to zero.
- MessageManager reads packaged defaults for missing messages while retaining custom values and leaving the server file untouched; this also makes the new full-wallet message available to existing installations.

Validation: nine boundary acceptance tests failed before correction. Fourteen temporary tests now cover the command/mission/daily/elixir XP paths, lifetime levels, normal gains, XP cancellation, full/partial token redemption, negative distribution configuration and translation fallback. One test compares 512 deterministic XP cases to an independent long-arithmetic oracle, including callback order. All 55 tests passed together. Temporary tests are removed before delivery. The database/API storage types remain int; amounts beyond representable stored capacity are capped instead of wrapping.

## Verified fixes: completed crafting and villager trades

- Crafting/trade clicks no longer award potential output before a transaction. `PlayerStatisticIncrementEvent` supplies the confirmed positive delta: crafted item count or completed trade count. This avoids full-inventory/no-op click credit and shift-click over/undercounting.
- Crafting requires an open CraftingInventory, excluding item-statistic updates from other result inventories. Trades read MerchantInventory.getMerchant rather than assuming the inventory holder is the villager, and preserve profession targets / the NONE-to-VILLAGER fallback. Custom merchants are excluded.
- One MONITOR/ignoreCancelled statistic handler is registered. The old public click entry points remain deprecated compatibility methods without event registration or counting, preventing duplicate awards.

Evidence: [Bukkit statistic event API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/player/PlayerStatisticIncrementEvent.html), [Paper/CraftBukkit 1.21.4 statistic dispatch](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.4/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java) and [completed trade statistic placement](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.4/paper-server/patches/sources/net/minecraft/world/inventory/MerchantResultSlot.java.patch). Eight temporary tests cover no-op clicks, actual shift output, cancellation/zero/unrelated statistics, profession targets, multiple trades and other inventory/merchant contexts. All 49 tests passed together. No live Minecraft server was used; a server/plugin that suppresses these statistics can also suppress this progress path.

## Verified fixes: database ordering, rollback and season finalization

- `DatabaseWriteQueue` orders mutations within this plugin instance and makes reads wait for earlier writes without blocking executor threads. It tracks outstanding reads/writes for shutdown and rejects new work once closing starts. Critical exceptional writes fence subsequent operations and request plugin disable; retryable player snapshot failures return false and remain retryable. Read-only failures do not poison the write lane.
- `DatabaseTransaction` wraps player snapshot updates, daily mission replacement and season-reset table changes. Failure rolls back the complete operation; failed rollback closes the connection instead of enabling auto-commit and risking a partial commit. Snapshot inputs are copied at the facade boundary.
- SQL errors loading claims/seasons/missions or performing critical writes now propagate instead of pretending success/empty data. Saving an empty active mission list clears the persisted list. A missing/blank current mission date retains the current-date fallback, while a failed query is an error.
- Season reward rotation, announcements and player reload run after the reset transaction commits. Progress is paused during reset. Startup/reset/write failures are observed and stop the plugin safely. Shutdown can finalize a committed pending reset directly, without waiting for a main-thread task that shutdown would cancel, and the finalizer is idempotent.

Validation: four initial SQLite/claim-read tests failed before correction. Thirteen temporary tests cover real SQLite rollback via aborting triggers, preserved player/mission state, empty replacement, retryable player writes, critical-write fences, ordered reads, shutdown draining, rollback failure, startup failure, delayed rotation and reset finalization during shutdown. All 54 tests passed together, including all 41 pre-existing tests. MySQL ordering/connection contracts were tested without a live MySQL server. This queue is local to one plugin instance, not a distributed lock across servers. Temporary tests are removed before delivery.

## Verified fixes: movement and elapsed play time (2026-10-24)

- All successful teleports reset the movement baseline and fractions, including short/same-world teleports. Changed worlds, large discontinuities and disabled-world movement also clear stale fractions. Cancelled events preserve valid state. Location snapshots are cloned and null destinations are ignored.
- Movement samples coordinate changes within a block, so a later flying/swimming/sneaking step does not inherit earlier walking distance. Fractional distances accumulate separately for each movement target and are retained when returning to that mode. Whole blocks still use the existing WALK_DISTANCE mission path.
- `PlayTimeTracker` centralizes periodic, world-change and quit accounting. Partial minutes survive periodic updates; elapsed time in a disabled world is excluded when the player returns. Teleport/respawn departure samples flush whole eligible minutes before leaving the current world. Eligible pending time waits for player data and for season-reset progress to resume. Session state is initialized for players already online when listeners register and is cleared on quit.
- Shutdown samples online play time before stopping XP events or saving players. Actionbar creation skips disabled plugins, so final progress can be saved without scheduling forbidden Bukkit tasks. Enabled-plugin actionbars still run normally.
- Movement/timing state is committed before calling mission progress. External event handlers may re-enter timing or teleport the player during a mission/XP callback; they cannot duplicate already-credited minutes or have their new teleport baseline overwritten by the old move.

Validation: eight initial movement/time regressions failed before correction; separate lifecycle checks also reproduced lost shutdown time and rejected scheduling during disable. All 71 tests passed together (41 pre-existing plus 30 temporary tests). Coverage includes mode fractions/sub-block moves, teleport cancellation, world/disabled-world transitions, departure/respawn accounting, location aliasing, cleanup, clock rollback/overflow, delayed data, reset pauses, quit/save ordering, existing online players and shutdown/actionbar ordering. Temporary tests and generated artifacts are removed before delivery. No live server was used. Fractional movement is rounded per target; incomplete play-time minutes remain session-local. Pending eligible time is only delivered while the current world permits missions; any time still pending on quit is session-local.

Two additional callback regressions then reproduced duplicate pending-minute credits and overwritten teleport baselines. After moving state acknowledgement before the callback, both pass together with the existing suite (43 tests). These additional temporary sources/artifacts are also removed before the final clean package.

## Verified fixes: atomic daily mission cycles (2026-10-24)

- `DatabaseManager.resetDailyMissions` delegates to `MissionCycleRepository`: active missions, season calendar/current mission date and deletion of all mission progress share one connection/transaction. Same-calendar-date resets also clear progress; a date-only `< currentDate` cleanup cannot distinguish two cycles on the same day. Player XP, levels, premium, claims, coins and the coin deadline are preserved.
- Regular, forced and overdue/missing-mission startup resets use that operation. Player snapshots are flushed and the cache generation invalidated first; failed preflight preserves the previous cycle. New missions/deadline/announcements/player reload are published after commit, while progress, duplicate daily resets and overlapping season resets are paused.
- Reload during a pending daily reset defers calendar recalculation until the new cycle is published, preventing an old date/deadline save from following the transaction. Shutdown can finalize a committed reset directly; its queued completion is idempotent. Scheduled checks prioritize the season reset when both deadlines expire.
- Existing separate save APIs remain available for saving unchanged state. Connection-level daily/season writers are shared with the composite operation; neither joins another queued job or borrows a nested MySQL connection.

Validation: six of seven initial temporary tests reproduced early publication, failed-preflight handling, partial calendar/mission replacement and retained same-day progress. The expanded suite passes 60 tests (41 existing plus 19 temporary): SQLite aborting triggers at each transaction stage, empty cycles, snapshot isolation, next-date reload, preserved player/coin state, one prefixed MySQL connection/commit, startup, reset guards, failure/disable, reload/shutdown completion and season priority. Temporary sources and generated artifacts are deleted before delivery. MySQL was tested through JDBC mocks, not a live database; transaction guarantees require transactional tables. The full season transition still uses its separate reset/calendar/mission operations and remains under review.

## Verified fixes: committed coin deliveries and timer lifecycle (2026-10-24)

- Pending/failed ranking reads no longer consume the delivery deadline. One pending attempt is allowed; cancellation, rescheduling, disabled shop and reset pauses prevent late/overlapping delivery. Startup deadline-read failure stops the plugin instead of silently creating another countdown. A lifecycle stop flag prevents a delayed callback from restarting the timer after disable.
- `CoinDistributionRepository` commits locked current-balance credits and the new deadline together. Offline balances no longer come from the older ranking snapshot. Amounts cap at wallet capacity and the actual credited amounts are returned for cache/messages; SQL failures roll back all wallets and the deadline. An expected-deadline conflict suppresses repeat credit without poisoning later writes.
- Player-cache preflight drains snapshots without holding the cache monitor, rejects unsaved data, invalidates old loads and defers new uncached loads/snapshots during the commit. Cached balances are changed only after success. Deferred loads resume afterwards, preventing a pre-credit load from restoring a stale balance or a post-credit load from being credited twice.

Validation: all three initial temporary task checks failed before correction (failed/pending read deadline loss and cancelled late delivery). All 71 tests now pass (41 existing plus 30 temporary). They cover SQLite rollback on wallet/deadline errors, competing connections for one deadline, current balances, caps/missing players/empty deliveries, retry/conflicts, MySQL row-lock SQL/single lease, cache load/save races and monitor ordering, stop/start callbacks, and integrated task/cache/database success/failure with a deposit made while ranking is pending. Temporary sources and generated artifacts are removed before delivery. MySQL was tested via JDBC mocks, not a live instance. The ranking read is asynchronous; player flushes and the wallet commit (at most ten recipients) wait on the main thread for consistency. This does not establish multi-server cache coherence for all player-state writes.

## Remaining review work

The audit is still active. Inspect and reproduce these candidates before changing behavior:

- Extreme reward configuration and progression scalability (very sparse/high levels); verify no loops scale with untrusted numeric ranges unnecessarily.
- GUI title collisions (including titles starting with a placeholder), reserved shop slots, item preview paths, pagination and malformed reward configuration.
- Full season transition atomicity across player reset, calendar, missions and filesystem reward rotation; daily/season time-accounting boundaries and reset-interval changes on reload/restart.
- Season reset/save ordering and time-accounting boundaries across daily/season resets; other startup/shutdown/reload paths, shared-database cache assumptions, editors, GUI caches, serialization, placeholders, configuration and optional integrations.

Review every production source file and relevant resource; track further findings and verification here. A live Minecraft or MySQL server has not been exercised so far.
