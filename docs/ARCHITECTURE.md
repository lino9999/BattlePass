# Architecture and change map

All Java paths below are relative to `src/main/java/com/Lino/battlePass/`.

## Lifecycle and data

`BattlePass.onEnable` creates managers, initializes the database asynchronously, then initializes missions on the Bukkit thread. Once missions are ready it loads online players, registers listeners/commands, starts scheduled work, and registers optional integrations. `listeners/EventManager` owns listener registration; MythicMobs is conditional.

Startup callbacks capture `BattlePass.lifecycleGeneration`; shutdown invalidates it before saving. Database completions, queued startup/failure work, mission readiness polls and coin restore callbacks must belong to the current enabled generation. `waitForMissions` disables the partial plugin on timeout; `completeStartup` owns successful registrations. `MissionManager` also checks shutdown/stopping before scheduling and executing its season/mission load continuations. Do not let an old enable operation mutate managers created by a later enable.

`models/PlayerData` is the cached mutable player state. `managers/PlayerDataManager` loads it, tracks dirty players via `markForSave(UUID)`, and saves snapshots. Changes to cached XP, coins, rewards or mission progress must be marked dirty. `managers/DatabaseManager` is the public facade for SQLite/MySQL persistence, delegating to the components below; `DatabaseSchema` handles additive migrations. Do not remove existing player data to fix a schema mismatch.

Dirty revisions are acknowledged individually: a completed write cannot clear a newer revision. Only one player write may be in flight per UUID; synchronous flush waits for it and writes any newer state. Player loads are coalesced, and a cache generation prevents loads started before a cache clear from repopulating it afterwards.

`clearCache(true)` refuses to discard unsaved revisions after a failed flush. Season reset runs this preflight before rotation/deadline changes. Player repositories resolve the mission date before acquiring the player connection, avoiding nested MySQL leases when the pool has only one connection.

`DatabaseWriteQueue` orders facade mutations and gates reads on earlier writes; callbacks compose futures rather than waiting on executor threads. Critical write exceptions fence subsequent operations and request plugin disable, while a player snapshot returning false remains retryable. Outstanding reads and writes are drained before closing connections. `DatabaseTransaction` owns an auto-commit connection for atomic snapshot/mission replacement/reset work and rolls back failures. Repositories must not join queued facade operations from inside their executor jobs.

Do not hold the player-cache monitor while waiting for database futures. A load completion running on the shared SQLite executor also needs that monitor; blocking it can prevent queued writes from ever completing. Only the generation check/cache mutation uses the monitor during clear.

`BattlePass.onDisable` flushes online play time, stops XP events/sounds/coin work, saves players synchronously, shuts down missions and closes the database. Final time progress happens before the XP multiplier is stopped; actionbar creation skips disabled plugins to avoid scheduling work during disable. Bukkit world, player and inventory operations belong on the server thread; database callbacks often schedule work back onto it.

## Feature map

| Feature | Main files | Related resources |
| --- | --- | --- |
| Missions and progress | `listeners/MissionProgressListener`, `managers/MissionManager`, `MissionGenerator`, `MissionProgressTracker`, `models/Mission` | `missions.yml`, `config.yml` |
| Daily and season deadlines | `MissionResetHandler`, `SeasonRotationManager`, `MissionManager` | `config.yml`; saved season start/end and mission date |
| Session movement/play time | `listeners/MovementMissionTracker`, `PlayTimeTracker`, `PlayerConnectionListener`, `tasks/BattlePassTask` | `missions.disabled-worlds`; WALK_DISTANCE/PLAY_TIME pool targets |
| Periodic coins | `tasks/CoinsDistributionTask`, `managers/CoinDistributionRepository`, `BattlePass.startCoinsDistributionTask` | `config.yml`; saved next-distribution deadline; [COINS.md](COINS.md) |
| Rewards and claims | `managers/RewardManager`, `models/Reward`, `gui/BattlePassGui` | `BattlePassFREE.yml`, `BattlePassPREMIUM.yml` |
| Custom vouchers/tokens/elixirs | `managers/CustomItemManager`, `listeners/CustomItemsListener` | PersistentDataContainer keys, `messages.yml` |
| Shop | `managers/ShopManager`, `gui/ShopGui`, `models/ShopItem` | `shop.yml` |
| Mission editing | `managers/MissionEditorManager`, `listeners/MissionEditorListener`, `gui/Mission*Gui` | Edits `missions.yml` pools |
| Reward editing | `managers/RewardEditorManager`, `listeners/RewardsEditorListener`, editor GUIs | Reward configuration |
| Menu input isolation | `gui/BaseHolder.MenuContext`, `listeners/GuiClickListener`, editor listeners | Typed inventory ownership; [MENUS.md](MENUS.md) |
| Commands and completion | `commands/BattlePassCommand`, `BattlePassTabCompleter` | `plugin.yml` permissions/aliases |
| Settings/messages/colors | `managers/ConfigManager`, `MessageManager`, `utils/GradientColorParser` | `config.yml`, `messages.yml` |
| Integrations/API | `placeholders/BattlePassExpansion`, `listeners/MythicMobsListener`, `events/BattlePassXPGainEvent` | `Placeholders.md`; optional plugins |

## Internal components: read the relevant file first

Public entry points keep their existing signatures. Their helpers are package-private, share existing dependencies/state, and are not additional Bukkit listeners or command registrations.

| Entry point | Internal component | Responsibility |
| --- | --- | --- |
| `managers/DatabaseManager` | `DatabaseConnections` | MySQL pool / SQLite connection, shared executor, table creation, connection shutdown |
| | `PlayerDataRepository` | Player load/save, claims, coins, leaderboard, season player reset |
| | `SeasonDataRepository` | Season dates, current mission date, coin deadline, ordered season save chain |
| | `DailyMissionRepository` | Active mission load/save and expired progress cleanup |
| | `MissionCycleRepository` | Atomic daily mission replacement, calendar/current date update and full mission-progress deletion |
| | `CoinDistributionRepository` | Expected-deadline check, locked current-wallet credits and atomic next-deadline update |
| `commands/BattlePassCommand` | `PlayerAdminCommands` | Player reset, premium, XP, coins, leaderboard inclusion |
| | `RewardAdminCommands` | Reward editor and custom item delivery |
| | `SeasonAdminCommands` | Reload and season/daily mission resets |
| | `XPEventCommands` | XP event start/stop |
| `listeners/MissionProgressListener` | `MovementMissionTracker` | Movement, teleports, world changes, distance buffers |
| | `BlockMissionTracker` | Place/break/mine, ore types, recent placements |
| | `CombatMissionTracker` | Damage, deaths, kills, projectile/entity targets |
| | `InteractionMissionTracker` | Food, crafting, fishing, trades, enchanting, XP pickup, animals |
| `gui/BattlePassGui` | `BattlePassRewardRenderer` | Free/premium reward rows, available/locked/claimed icons, reward lore and row separator |
| | `BattlePassMenuControls` | Progress summary, navigation tags and action buttons |
| `managers/RewardManager` | `RewardClaimHandler` | Claim eligibility, persisted claim checks, command/item delivery, feedback and dirty marking |

`BattlePassGui` owns the existing player-data/page/max-level snapshot and inventory creation/opening. It passes that same snapshot to its renderers and preserves rendering order: progress, rewards, separator, navigation, actions. Renderers only populate the inventory; click handling stays in the existing listeners. `RewardManager` retains reward loading, reward maps, level/page limits and editable reward conversion; its public claim method delegates to `RewardClaimHandler` with unchanged arguments. Neither extraction changes configuration paths, slot numbers, persistent tags or claim/save ordering.

Keep event annotations and registration on `MissionProgressListener` so priorities, cancellation handling and registration order stay unchanged. Join/quit initialization and cleanup delegate to both movement and block state. `DatabaseManager.shutdown` waits for ordered season writes before stopping the shared executor and closing connections. Mission-date and villager-profession callbacks preserve dispatch through the original public methods.

`EventManager` also seeds movement/timing for players already online at listener registration. `PlayerConnectionListener` owns `PlayTimeTracker`; periodic, world-change, quit and shutdown updates share one elapsed-time accumulator. Partial minutes and per-mode movement fractions are session-local, with disabled-world time excluded. `MissionManager.isProgressPaused()` exposes the existing reset/shutdown gate so timed credits can wait rather than disappear during a reset.

Season calendars are prepared before their queued save, but reward rotation/announcements/player reload wait for the reset transaction to commit. Mission progress is paused while resetting. The idempotent commit callback can run directly during main-thread shutdown after awaiting the raw database reset, avoiding dependence on a cancelled Bukkit task. Failure disables the plugin and leaves later database writes fenced.

Daily resets (automatic, forced and startup recovery) flush/invalidate player snapshots before one queued `resetDailyMissions` transaction. `MissionCycleRepository` uses the existing connection-level daily/season writers and clears all progress, including same-date rows. `MissionManager` publishes the replacement and next deadline only after commit; its pending daily finalizer also runs directly during shutdown. Calendar reloads defer while that reset is pending. Do not replace this with separate saves/deletes or clear progress by date alone. Season expiry takes precedence over a scheduled daily reset. Full season rotation still has separate persistence/filesystem steps; see CODE_AUDIT for remaining review work.

`season_data.mission_cycle_start` is an additive nullable TEXT column storing the daily cycle's start alongside its deadline, including in the daily-reset transaction. `MissionResetHandler` recalculates `start + missions.reset-hours` on reload/restart, retaining overdue deadlines so the reset actually runs. Never advance past a missed reset in a loop. Legacy rows without this anchor preserve their stored deadline once by inferring a start using the current interval. Public database save/reset signatures stay unchanged: the facade derives/captures the anchor from the submitted deadline and current interval before queueing. During a pending daily or season reset, calendar reload is deferred until commit; the daily finalizer restores its captured start rather than inferring it from a possibly changed interval.

Crafting and villager trading use the facade's MONITOR/ignoreCancelled statistic handler for completed action counts. Their old public click methods remain deprecated, unregistered compatibility stubs. Do not restore click estimates alongside statistics; that would award twice.

## Invariants worth preserving

- Season duration is counted from the persisted season start. Reload/restart must not restart the countdown. Legacy duration seasons without a saved start get a one-time new countdown.
- Daily reset intervals are counted from the persisted mission cycle start. A shorter interval may make the cycle due immediately; a longer interval may preserve the same cycle. Exact deadlines are due and overdue countdown text is clamped to zero.
- Saves must not let an older season deadline overwrite a newer one. New missions are saved after season-reset deletion completes.
- Coin distribution has one managed task. Rescheduling/manual season resets preserve an existing delivery deadline.
- Coin delivery flushes/invalidate-loads before its atomic database commit, then updates cached balances and notifications. Never credit from the old leaderboard balance or advance a deadline before credit success. See COINS.md for cache gates and the synchronous commit boundary.
- XP grants fire `BattlePassXPGainEvent`; cancellation and modified nonnegative amounts are respected.
- Use `utils/ExperienceProgression` for XP accumulation/level conversion so the wide total survives until levels are deducted; its callback preserves per-level feedback. XP events/storage remain int-valued and lifetime counts/remaining XP cap at the representable maximum.
- Credit currency through `PlayerData.addBattleCoins` and use the returned credited amount. Coin-token redemption must retain any uncredited tokens at the wallet limit; configured distributions cannot be negative.
- Custom item identity comes from persistent tags, not material/name alone. Redeeming a stack must consume the same quantity used for the reward and the correct hand.
- Loaded item rewards use `Reward.configurationPath` for exact item/metadata selection. Do not rediscover an entry by material: multiple rewards may share a material. Claims enforce cached claim/level/premium eligibility in `RewardManager`; shop delivery must clone configured item stacks.
- XP event duration input must be fully consumed and checked for overflow. Invalid API starts preserve the current event; replacing an expired event still releases its pending task and bossbar.
- GUI handlers check `BaseHolder.isBattlePassInventory` before cancelling or altering another inventory.
- `BaseHolder.MenuContext` determines menu kind, page and editing session; titles/labels never dispatch actions. Main reload rebuilds only owned inventories and does not scan numeric reward ranges. See MENUS.md for factory compatibility and pagination rules.
- Read-only menu actions use top-inventory raw slots; bottom inventory clicks must never trigger a button. Top-inventory drags are blocked while bottom-only and foreign drags remain independent.
- Reward rows take precedence over material-based buttons; configurable reward icons may use action-button materials. Controls/back buttons require their fixed slots.
- Version comes from `pom.xml` and is filtered into `plugin.yml`. Do not hardcode a second descriptor version.
- `MessageManager` loads packaged message defaults in memory for missing keys; custom translations win and the server's file is not rewritten.
