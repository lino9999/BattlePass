# Mission progress and food troubleshooting

## Data flow and edit locations

1. `src/main/resources/missions.yml` supplies `mission-pools`. `MissionEditorManager` writes the server's pool file; `ConfigManager` loads it.
2. `MissionGenerator` picks weighted templates, expands `<amount>`/`<target>`, and creates `Mission` objects. `MissionManager` restores active daily missions from the database or generates a new set at reset.
3. `listeners/MissionProgressListener` retains Bukkit event registration/annotations and delegates to `MovementMissionTracker`, `BlockMissionTracker`, `CombatMissionTracker` or `InteractionMissionTracker`. These translate events into `MissionManager.progressMission(player, type, target, amount)`. Mythic mob kills have a separate listener; timed play progress comes from `tasks/BattlePassTask`.
4. `MissionManager` rejects nonpositive amounts and disabled worlds. `MissionProgressTracker` matches type/targets, clamps progress, prevents repeated completion, grants XP, shows actionbars and marks player data dirty.
5. `DatabaseManager` persists active missions and player progress; the GUI reads active missions/progress, not templates directly.

Editing a pool or using `/bp reload` does not replace the already active daily missions. To test a new pool, use a disposable server and generate a new set with `/bp reset missions` (this resets current mission progress). Do not reset a production server merely to diagnose an event.

Progress keys are `type + "_" + target + "_" + required + "_" + name.hashCode()`. Keep stored fields unchanged when improving comparison; normalizing stored fields can orphan existing progress. Primary target `ANY` or null is a wildcard; `additional-targets` are alternatives, and one action counts once even if multiple alternatives match.

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

Food target comparison accepts Bukkit material spellings (for example `Golden Apple`, `minecraft:golden_apple`) as well as canonical IDs and surrounding whitespace. It uses `Material.matchMaterial` only for `EAT_ITEM`, including additional targets; custom mob identifiers retain their existing text matching. Prefer canonical singular material IDs in configuration.

## Golden apple report: evidence and limits

The 2026-10-23 customer report had no server version or configuration available. Canonical `GOLDEN_APPLE` and `ENCHANTED_GOLDEN_APPLE` already had a valid consume-event path. The reproduced failure was formatted/namespaced food targets failing the previous plain string comparison; the fix resolves them as materials without changing stored keys. This does not establish that the customer's configuration was the same failure.

Temporary regression tests verified canonical/formatted/namespaced names, additional targets and distinct apple variants. They also exercised Bukkit cancellation dispatch, both hands, full/reduced hunger, one unit per stack consumption, wildcard missions and the dirty-save marker through the real progress tracker, with a mocked MissionManager bridge. The full build passed 55 tests on 2026-10-23 before the two temporary test sources and their generated classes/reports were removed at the owner's request. These temporary tests are not retained in the current repository.

If a canonical target still does not count on a live server, collect the active mission target/type, server/plugin versions, disabled worlds, player-data loading status and plugins cancelling/replacing consumption. Check those before adding fallback statistic/right-click listeners, which could award duplicate or unconsumed items.

## WALK_DISTANCE and PLAY_TIME

`MovementMissionTracker` records coordinate changes (including within a block) with separate fractional buffers for WALK, FLY, SWIM and SNEAK. A whole block is sent through the normal mission path with that mode; ANY missions match those updates as usual. Fractions remain associated with their own mode. Teleports, world changes, invalid/large discontinuities and disabled worlds reset the baseline/fractions; cancelled movement/teleports do not. Stored Location objects are snapshots, not mutable event references.

`listeners/PlayTimeTracker`, owned by `PlayerConnectionListener`, accounts elapsed milliseconds and retains partial minutes between updates. `BattlePassTask` delegates periodic updates to the connection listener. World changes delimit eligible and disabled-world time; teleport/respawn handlers sample departure time before leaving the current world. Only eligible time contributes, and pending eligible minutes wait for loaded player data, unpaused progress and a currently enabled world. Join/listener registration initialize timing; quit flushes whole eligible minutes before player saving and clears session state. Incomplete minutes are not persisted between sessions. The existing `getPlayTimeStart()` map exposes last sampling timestamps; uncredited milliseconds are held internally.

`BattlePass.onDisable` flushes online timing before stopping XP events and saving players. Progress/XP can still be committed at shutdown; `MissionProgressTracker` avoids scheduling actionbars while the plugin is disabled. Do not restore separate minute arithmetic in the periodic task or quit handler, which would lose fractions or award elapsed time twice. See CODE_AUDIT for automated evidence and lifecycle limits.

Commit movement/timing sample state before invoking mission progress: XP/mission callbacks can trigger another timer sample or teleport. Writing the old sample after those callbacks can duplicate time or undo the new teleport baseline.

## CRAFT_ITEM and TRADE_VILLAGER

`MissionProgressListener.onStatisticIncrement` delegates to `InteractionMissionTracker` at MONITOR with `ignoreCancelled=true`. Positive `CRAFT_ITEM` statistic deltas count actual output only while a CraftingInventory is open; positive `TRADED_WITH_VILLAGER` deltas count transactions while a MerchantInventory with a Villager merchant is open. The trade target is the profession, with NONE mapped to VILLAGER.

Do not count recipe-result/trade-result clicks or estimate shift output from ingredient stacks. A click may take nothing or only part of the potential batch. The deprecated `onCraftItem`/`onVillagerTrade` public methods are compatibility stubs and deliberately have no event registration/counting; re-enabling them would double-count confirmed statistics. Other result inventories/custom merchants are excluded. Servers/plugins suppressing the relevant statistics can suppress progress; inspect the [1.21.4 dispatch source](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.4/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java) when diagnosing that interaction.
