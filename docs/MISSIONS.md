# Mission progress and food troubleshooting

## Data flow and edit locations

1. `src/main/resources/missions.yml` supplies `mission-pools`. `MissionEditorManager` writes the server's pool file; `ConfigManager` loads it.
2. `MissionGenerator` picks weighted templates, expands `<amount>`/`<target>`, and creates `Mission` objects. `MissionManager` restores active daily missions from the database or generates a new set at reset.
3. `listeners/MissionProgressListener` translates Bukkit events into `MissionManager.progressMission(player, type, target, amount)`. Mythic mob kills have a separate listener; timed play progress comes from `tasks/BattlePassTask`.
4. `MissionManager` rejects nonpositive amounts and disabled worlds. `MissionProgressTracker` matches type/targets, clamps progress, prevents repeated completion, grants XP, shows actionbars and marks player data dirty.
5. `DatabaseManager` persists active missions and player progress; the GUI reads active missions/progress, not templates directly.

Editing a pool or using `/bp reload` does not replace the already active daily missions. To test a new pool, use a disposable server and generate a new set with `/bp reset missions` (this resets current mission progress). Do not reset a production server merely to diagnose an event.

Progress keys are `type + "_" + target + "_" + required + "_" + name.hashCode()`. Keep stored fields unchanged when improving comparison; normalizing stored fields can orphan existing progress. Primary target `ANY` or null is a wildcard; `additional-targets` are alternatives, and one action counts once even if multiple alternatives match.

## EAT_ITEM

`MissionProgressListener.onPlayerConsume` listens at `MONITOR`, with `ignoreCancelled=true`, and reads `event.getItem().getType()`. A successful `PlayerItemConsumeEvent` contributes **one** item, regardless of stack size or hand. Do not count right-clicks, inspect only the main hand, or require the food level to increase: golden apples are usable at full hunger. Cancelled consumption must not grant progress.

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

`EatMissionTargetTest` covers canonical/formatted/namespaced names, additional targets and distinct apple variants. `EatMissionProgressTest` exercises Bukkit cancellation dispatch, both hands, full/reduced hunger, one unit per stack consumption, wildcard missions and the dirty-save marker through the real progress tracker; its MissionManager bridge is mocked.

If a canonical target still does not count on a live server, collect the active mission target/type, server/plugin versions, disabled worlds, player-data loading status and plugins cancelling/replacing consumption. Check those before adding fallback statistic/right-click listeners, which could award duplicate or unconsumed items.
