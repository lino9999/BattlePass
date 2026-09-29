# GitHub issue review — 2026-09-29

Reviewed all 11 issues and their discussions against the local source. GitHub currently has three open issues: #9, #20 and #21. The website's cached issue listing was older than the API results.

| Issue | Finding and result |
| --- | --- |
| [#1 — XP API](https://github.com/lino9999/BattlePass/issues/1) | Events already fire for missions, daily rewards and XP elixirs. Fixed the missing Bukkit `Cancellable` interface; verified `RegisteredListener` now respects `ignoreCancelled`. |
| [#2 — GUI interference](https://github.com/lino9999/BattlePass/issues/2) | Click and drag handlers already check inventory ownership. Added the same check to the reward editor close handler. Tests verify unrelated inventories are ignored even with a matching editor title. |
| [#8 — License](https://github.com/lino9999/BattlePass/issues/8) | Already satisfied: the repository contains an MIT `LICENSE`. |
| [#9 — Custom heads and menu layouts](https://github.com/lino9999/BattlePass/issues/9) | Confirmed feature limitation, rather than a regression. Left out at the owner's request: this pass covers bug fixes, not new menu features. |
| [#11 — Minecraft 1.20.1](https://github.com/lino9999/BattlePass/issues/11) | A compatibility question, already answered. This project targets Java 21 and the Spigot 1.21.4 API with `api-version: '1.21'`; no 1.20.1 backport is included. |
| [#16 — Custom model data](https://github.com/lino9999/BattlePass/issues/16) | Existing reward icon and page arrow options load correctly, including reloads. The supported syntax is shown below. |
| [#17 — Disabled worlds](https://github.com/lino9999/BattlePass/issues/17) | Existing mission world blacklist loads correctly and both mission progress entry points check it. The key is `missions`, not `misions` as misspelled in the earlier issue reply. This disables mission progress, not every plugin feature. |
| [#18 — Actionbar duration](https://github.com/lino9999/BattlePass/issues/18) | Existing progress/completion duration settings load correctly and are used by the actionbar tasks. The key is `missions`, not `misions`. Taking exclusive control of other plugins' actionbars remains outside the implemented feature, as the existing issue reply explains. |
| [#19 — Coins and Current Season translation](https://github.com/lino9999/BattlePass/issues/19) | Verified overdue deliveries add the configured amount to the current cached balance once and save the next deadline. Fixed manual season reset postponing an existing delivery and missing legacy coin schedule columns. `items.progress.current-season` already controls the displayed translation. |
| [#20 — Incorrect installed version](https://github.com/lino9999/BattlePass/issues/20) | Confirmed in the attached log and source: the descriptor still said 8.2. Version 8.6 is now defined once in `pom.xml` and injected into the packaged descriptor. Older releases no longer trigger update notices. |
| [#21 — MySQL migration and version](https://github.com/lino9999/BattlePass/issues/21) | Confirmed invalid MySQL migration SQL and swallowed exceptions. Added shared, additive schema checks without a `TEXT` default. Missing columns are migrated; genuine failures abort startup with the failing table/column. Version reporting is fixed as above. |

The MySQL migration previously used `TEXT DEFAULT ''`, which is invalid in MySQL. The [MySQL default-value documentation](https://dev.mysql.com/doc/refman/8.0/en/data-type-defaults.html) explains the restriction. Existing rows now get nullable `additional_targets`, which the mission loader already handles as an empty list.

## Configuration examples

In `config.yml`:

```yaml
missions:
  disabled-worlds:
    - world_nether
  actionbar:
    progress-duration: 10
    completed-duration: 15

gui:
  reward-locked:
    free:
      material: GRAY_STAINED_GLASS
      custom-model-data: 101
    premium:
      material: GRAY_STAINED_GLASS
      custom-model-data: 102
  navigation:
    custom-model-data: 110
```

In `messages.yml`, under `items.progress`:

```yaml
current-season: "&7Stagione attuale: &e%current_season%"
```

## Validation and remaining server checks

The Maven suite covers season duration and restart behavior, legacy SQLite database migrations with preserved player data, migration SQL/error handling through mocked JDBC, coin delivery and scheduling, GUI isolation, XP event cancellation, configuration reloads, version comparison and the packaged plugin descriptor.

SQLite migration tests use a real database. MySQL migration tests verify JDBC behavior and the SQL contract; no live MySQL server or Minecraft server was available in this workspace. The owner should still smoke-test the resulting plugin on their server. No issue comments or issue state changes were posted during this review.

The earlier season-duration fix is included. On the first upgrade, legacy `DURATION` seasons without a saved start date receive a new full countdown once, preserving player progress. Afterwards, duration edits apply from the saved start date; routine reloads and restarts do not restart the countdown.
