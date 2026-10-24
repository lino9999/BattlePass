# PlaceholderAPI request and cache map

Read this for scoreboard/hologram stalls, global placeholders, leaderboard values, mission identifiers or XP percentages. Public names/examples remain in the root `Placeholders.md`. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Identifier routing, global/personal separation and mission slots | `placeholders/BattlePassExpansion` |
| SQL coalescing, immutable top-ten snapshots, names and shutdown | `placeholders/LeaderboardPlaceholders` |
| SQL leaderboard order/exclusions | `managers/DatabaseManager.getTop10Players`, `PlayerDataRepository` |
| Registration/terminal expansion shutdown | `BattlePass.registerPlaceholders`, `onDisable` |

Normalize identifiers once with Locale.ROOT, including their dynamic prefixes/fields. Unknown identifiers return null, malformed mission/top requests return an empty string. Mission indices are validated before subtracting one; strip exactly the named prefix rather than replacing arbitrary embedded tokens. Mission progress keys retain type, raw target, required amount and name hash.

Season/mission/coin/event timers, season counts, total missions, mission names and top-ten values are global and accept a null viewing player. Rank needs the player's UUID but not its cached PlayerData. Personal statistics, mission progress/status and personal rewards still require PlayerData; absent data returns `Loading...`, while a missing player context returns an empty string. Available rewards retain the online-only count contract. Percentages use wide multiplication/division and a minimum cost of one, without imposing a new 100-percent cap on carried XP.

Never join a SQL future in a placeholder request. All rank/top readers share one on-demand refresh and an immutable snapshot of at most ten rows, with a five-second monotonic refresh interval after completion/failure. Cold requests return `Loading...`; warmed requests keep the previous snapshot while a refresh is pending or fails. Failures log once per attempted refresh and retry after the interval. A successful empty query yields `Empty` for a missing position and `Unranked` for a UUID outside the top ten. Numeric values are copied before publication so later mutable PlayerData changes cannot alter the cached snapshot.

Database completion may run off the server thread. Resolve offline names through Bukkit only on the server thread, once per refresh; preserve ordering and use `Unknown` when a profile has no name. SQL waits are removed from requests, but this does not promise that the server's own offline-profile lookup has zero I/O cost. The cached rank/order is the SQL result, including its exclusion and lifetime-level ordering, and can lag database changes until the next successful refresh.

One request object owns pending SQL/name-publication work. Shutdown detaches it before cancelling a queued publication task; late callbacks cannot publish, look up names or reset newer ownership. `BattlePass.onDisable` calls expansion.shutdown before PlaceholderAPI's final unregister method. Old expansion instances remain closed after re-enable; a new expansion has a fresh cache. Cancellation failure is logged without aborting the rest of plugin shutdown. No repeating cache task, persisted cache, new configuration key or database migration is introduced.

The regression record in `CODE_AUDIT.md` covers actual worker completions/concurrent readers with mocked Bukkit scheduling/profiles and the existing SQLite suite. No live PlaceholderAPI scoreboard/hologram server was exercised.
