# Mission progress and material target troubleshooting

## Data flow and edit locations

1. `src/main/resources/missions.yml` supplies `mission-pools`. `MissionEditorManager` writes the server's pool file; `ConfigManager` loads it.
2. `MissionGenerator` picks weighted templates, expands `<amount>`/`<target>`, and creates `Mission` objects. `MissionManager` restores active daily missions from the database or generates a new set at reset.
3. `listeners/MissionProgressListener` retains Bukkit event registration/annotations and delegates to `MovementMissionTracker`, `BlockMissionTracker`, `CombatMissionTracker` or `InteractionMissionTracker`. These translate events into `MissionManager.progressMission(player, type, target, amount)`. Mythic mob kills have a separate listener; timed play progress comes from `tasks/BattlePassTask`.
4. `MissionManager` rejects nonpositive amounts and disabled worlds. `MissionProgressTracker` matches type/targets, clamps progress, prevents repeated completion, grants XP, shows actionbars and marks player data dirty.
5. `DatabaseManager` persists active missions and player progress; the GUI reads active missions/progress, not templates directly.

Editing a pool or using `/bp reload` does not replace the already active daily missions. To test a new pool, use a disposable server and generate a new set with `/bp reset missions` (this resets current mission progress). Do not reset a production server merely to diagnose an event.

Progress keys are `type + "_" + target + "_" + required + "_" + name.hashCode()`. Keep stored fields unchanged when improving comparison; normalizing stored fields can orphan existing progress. Primary target `ANY` or null is a wildcard; `additional-targets` are alternatives, and one action counts once even if multiple alternatives match.

Daily reset uses `DatabaseManager.resetDailyMissions`: mission rows, calendar/date and deletion of all player mission progress commit together. It intentionally clears same-calendar-date progress too, since a forced reset or an interval shorter than 24 hours can start another cycle that day. Player XP/rewards/coins remain unchanged. Pending resets pause mission progress and publish the new list only after commit; failed player-cache flush postpones the reset. Do not restore the former independent save/save/date-cleanup sequence. An expired season takes precedence over the scheduled daily cycle.

Mission range upper bounds and weight totals use long arithmetic even though configured/generated values are ints. Progress additions also widen before clamping to `required`; do not reintroduce int overflow by adding first and clamping afterwards.

## EAT_ITEM

`MissionProgressListener.onPlayerConsume` listens at `MONITOR`, with `ignoreCancelled=true`, and delegates to `InteractionMissionTracker.onPlayerConsume`, which reads `event.getItem().getType()`. A successful `PlayerItemConsumeEvent` contributes **one** item, regardless of stack size or hand. Do not count right-clicks, inspect only the main hand, or require the food level to increase: golden apples are usable at full hunger. Cancelled consumption must not grant progress.

Canonical pool example:

```yaml
mission-pools:
  eat-golden-apples:
    type: EAT_ITEM
    target: GOLDEN_APPLE
    additional-targets:
      - ENCHANTED_GOLDEN_APPLE
    display-name: "Eat <amount> Golden Apples"
    min-required: 3
    max-required: 3
    min-xp: 100
    max-xp: 100
    weight: 10
```

Omit `additional-targets` to require ordinary golden apples only; use `target: ENCHANTED_GOLDEN_APPLE` for enchanted apples only. These are distinct materials. The displayed name does not select what counts.

Material target comparison accepts Bukkit material spellings (for example `Golden Apple`, `minecraft:golden_apple`, `minecraft:dirt`) as well as canonical IDs and surrounding whitespace. Material.matchMaterial applies to EAT_ITEM, BREAK_BLOCK, MINE_BLOCK, PLACE_BLOCK, CRAFT_ITEM, SMELT_ITEM, ENCHANT_ITEM and FISH_ITEM, including additional targets. Other types/custom mob identifiers retain their existing literal text matching. Prefer canonical singular material IDs in configuration. This comparison never rewrites stored target/type/name fields or progress keys.

The catalog comes from the running server, rather than a plugin-maintained block list. See [COMPATIBILITY.md](COMPATIBILITY.md) for verification across Spigot/Paper 1.21.11–26.3. BREAK_BLOCK and PLACE_BLOCK accept every server-exposed block; other material mission types still require their corresponding action (for example eating edible items or mining ores). A block added in 26.3 cannot occur on a 1.21.11 server, so a shared pool must use targets available on each server or suitable additional-targets. No material backport or custom datapack/mod block registry is provided.

## Golden apple report: evidence and limits

The 2026-10-23 customer report had no server version or configuration available. Canonical `GOLDEN_APPLE` and `ENCHANTED_GOLDEN_APPLE` already had a valid consume-event path. The reproduced failure was formatted/namespaced food targets failing the previous plain string comparison; the fix resolves them as materials without changing stored keys. This does not establish that the customer's configuration was the same failure.

Temporary regression tests verified canonical/formatted/namespaced names, additional targets and distinct apple variants. They also exercised Bukkit cancellation dispatch, both hands, full/reduced hunger, one unit per stack consumption, wildcard missions and the dirty-save marker through the real progress tracker, with a mocked MissionManager bridge. The full build passed 55 tests on 2026-10-23 before the two temporary test sources and their generated classes/reports were removed at the owner's request. These temporary tests are not retained in the current repository.

If a canonical target still does not count on a live server, collect the active mission target/type, server/plugin versions, disabled worlds, player-data loading status and plugins cancelling/replacing consumption. Check those before adding fallback statistic/right-click listeners, which could award duplicate or unconsumed items.

## BREAK_BLOCK and the DIRT report (2026-10-24)

Registered MONITOR/ignoreCancelled block-break dispatch emits BREAK_BLOCK with the actual block's material name and amount one. The path does not require dropped items. The canonical type is BREAK_BLOCK; a raw type BREAK does not match. MINE_BLOCK is a separate ore-only path requiring item drops; DIRT is not a mining ore.

Canonical BREAK_BLOCK + DIRT already counts and marks progress dirty. GRASS_BLOCK is distinct from DIRT even when its drop is dirt; include it explicitly if desired:

```yaml
type: BREAK_BLOCK
target: DIRT
additional-targets:
  - GRASS_BLOCK
```

The packaged break-dirt pool already includes GRASS_BLOCK, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, DIRT_PATH and FARMLAND. Its generated mission counts these eight block types including DIRT. Disabled worlds, paused progress, unloaded player data and cancelled breaks still prevent counting.

The customer follow-up did not include the active mission/server version. Tests reproduced failures for namespaced/whitespace block targets and alternatives, which the material comparison now resolves for all eight material-based mission types. The exact customer's cause remains unverified. The initial 14 report checks had eleven passing unchanged cases and three failing formatted-target cases; all 94 tests passed after correction (41 existing plus 53 temporary). Coverage includes the actual block event/registered cancellation handler, real MissionManager/progress tracker, packaged generation, dirty marking, raw progress keys, duplicate alternatives, all material target types, ore/drop/world/data gates and unchanged custom identifiers. Bukkit player/scheduler behavior is mocked; no live server was used. Temporary sources/artifacts are removed before delivery.

## Mission progress ownership

The 1.21.11–26.3 compatibility checks also reproduced uppercase namespaces written by the mission editor (minecraft:dirt becomes MINECRAFT:DIRT). Material lookups now normalize case with Locale.ROOT before Bukkit resolves the prefix, for primary and additional targets. Stored fields/keys and nonmaterial identifiers remain unchanged. See COMPATIBILITY.md for the complete catalog matrix and live server evidence.

MissionProgressTracker derives completion from PlayerData.missionProgress rather than a second completed-key cache. resetplayer clears progress and calls mission cleanup to invalidate pending gain hooks and remove old actionbars; an already completed mission can then progress again. Hooks recheck the runtime data revision/cache identity and exact completion value before awarding XP. Progress keys and cancellation behavior remain unchanged. See EXPERIENCE.md for reentry, dirty marking and final shutdown credits.

## WALK_DISTANCE and PLAY_TIME

`MovementMissionTracker` records coordinate changes (including within a block) with separate fractional buffers for WALK, FLY, SWIM and SNEAK. A whole block is sent through the normal mission path with that mode; ANY missions match those updates as usual. Fractions remain associated with their own mode. Teleports, world changes, invalid/large discontinuities and disabled worlds reset the baseline/fractions; cancelled movement/teleports do not. Stored Location objects are snapshots, not mutable event references.

`listeners/PlayTimeTracker`, owned by `PlayerConnectionListener`, accounts elapsed milliseconds and retains partial minutes between updates. `BattlePassTask` delegates periodic updates to the connection listener. World changes delimit eligible and disabled-world time; teleport/respawn handlers sample departure time before leaving the current world. Only eligible time contributes, and pending eligible minutes wait for loaded player data, unpaused progress and a currently enabled world. Join/listener registration initialize timing; quit flushes whole eligible minutes before player saving and clears session state. Incomplete minutes are not persisted between sessions. The existing `getPlayTimeStart()` map exposes last sampling timestamps; uncredited milliseconds are held internally.

`BattlePass.onDisable` flushes online timing before stopping XP events and saving players. Progress/XP can still be committed at shutdown; `MissionProgressTracker` avoids scheduling actionbars while the plugin is disabled. Do not restore separate minute arithmetic in the periodic task or quit handler, which would lose fractions or award elapsed time twice. See CODE_AUDIT for automated evidence and lifecycle limits.

Commit movement/timing sample state before invoking mission progress: XP/mission callbacks can trigger another timer sample or teleport. Writing the old sample after those callbacks can duplicate time or undo the new teleport baseline.

## CRAFT_ITEM and TRADE_VILLAGER

`MissionProgressListener.onStatisticIncrement` delegates to `InteractionMissionTracker` at MONITOR with `ignoreCancelled=true`. Positive `CRAFT_ITEM` statistic deltas count actual output only while a CraftingInventory is open; positive `TRADED_WITH_VILLAGER` deltas count transactions while a MerchantInventory with a Villager merchant is open. The trade target is the profession, with NONE mapped to VILLAGER.

Do not count recipe-result/trade-result clicks or estimate shift output from ingredient stacks. A click may take nothing or only part of the potential batch. The deprecated `onCraftItem`/`onVillagerTrade` public methods are compatibility stubs and deliberately have no event registration/counting; re-enabling them would double-count confirmed statistics. Other result inventories/custom merchants are excluded. Servers/plugins suppressing the relevant statistics can suppress progress; inspect the [1.21.4 dispatch source](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.4/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java) when diagnosing that interaction.

## DAMAGE_TAKEN and DAMAGE_DEALT

Both combat paths share CombatMissionTracker.calculateDamageAmount. Use getFinalDamage, which represents damage after reductions ([Spigot API contract](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/entity/EntityDamageEvent.html#getFinalDamage())). Zero, negative and nonfinite final amounts contribute nothing; do not fall back to raw damage or take an absolute value. Preserve ordinary Math.round behavior and a minimum of one for a positive fraction. Clamp the rounded long to Integer.MAX_VALUE before casting, so a large finite boss/plugin hit cannot wrap to zero/negative progress. This retains the existing integer-per-hit policy rather than adding fractional accumulation or a victim-health cap.

MissionProgressListener still dispatches at MONITOR/ignoreCancelled. Projectile shooter attribution, victim/source alternatives, FIRE/EXPLOSION aliases and distinct attacker/victim mission types remain unchanged. A fully shielded/reduced hit grants no damage progress; partial positive damage still counts even while blocking.

## Optional MythicMobs hook

EventManager registers the six core listeners first and checks isPluginEnabled("MythicMobs") before creating its optional listener. Installed-but-disabled plugins do not activate the hook. Runtime registration failures or linkage/API errors warn and leave core startup running; attempt to unregister only the failed optional listener and report any cleanup failure separately. Announce hook success after registration succeeds. MythicMobsListener continues forwarding player kills and internal mob names through normal MissionManager world/reset gates.

Hook detection occurs during BattlePass listener startup. An unavailable hook cannot advance configured KILL_MYTHIC_MOB missions; compatible MythicMobs must be enabled when BattlePass starts. Tests verify registration/error isolation, including a real absent optional API at event-method reflection time, but no live MythicMobs server was used.
