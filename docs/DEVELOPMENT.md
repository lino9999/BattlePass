# Build, tests and delivery

## Toolchain

Use JDK 21 and Maven 3.9.x (`java -version`, `mvn -version`). There is no checked-in Maven wrapper. The project compiles against Spigot API `1.21.4-R0.1-SNAPSHOT` and declares `api-version: '1.21'`. Do not assume compatibility with older Minecraft versions.

```powershell
mvn test
mvn clean package
```

The packaged plugin is `target/BattlePass-<version>.jar`; `target/original-*.jar` is the pre-shade artifact. The current version is in `pom.xml`. Source resources are filtered during packaging. Maven Shade may generate `dependency-reduced-pom.xml`; it is ignored.

First builds need network access to the repositories declared in `pom.xml`, including Spigot, PlaceholderAPI and Lumine. Download caches/tooling belong outside Git. Do not replace optional API dependencies with local stubs to make a failing build look successful.

## Focused test map

| Area | Tests (class names) |
| --- | --- |
| Season countdown/reload/reset ordering | `MissionManagerSeasonTest`, `DatabaseSeasonDataTest` |
| Schema migration/preserved data | `DatabaseSchemaTest` |
| Coins and single task/deadline | `CoinsDistributionTaskTest`, `CoinsDistributionSchedulingTest` |
| Inventory ownership | `GuiIsolationTest` |
| XP cancellation | `BattlePassXPGainEventTest` |
| Config keys/reload | `ReportedConfigurationTest` |
| Version and descriptor | `VersionUtilsTest`, `PluginMetadataTest` |

JUnit Jupiter + Mockito are configured in `pom.xml`. SQLite tests use a real temporary database; MySQL uses mocked JDBC. Surefire excludes Mythic-Dist because its bundled SLF4J classes conflict with the test runtime. New listener tests can mock static `Bukkit` and the scheduler; use `RegisteredListener` when checking `ignoreCancelled`, since invoking the handler directly bypasses Bukkit's dispatch rules.

Owner policy: never commit or push test files. Create any new regression tests temporarily under `src/test/` (ignored for new files), run them, then delete their sources and generated classes/reports before staging. Keep pre-existing tracked tests unless explicitly asked to remove them. Record the tested behavior and result in documentation; temporary tests are not part of the delivered repository.

Automated tests do not replace a live Minecraft smoke test. Report exactly what was run and distinguish a reproduced failure from a suspected customer environment issue.

## Structural refactor verification (2026-10-23)

The database facade, command router and mission listener were split into internal components without changing SQL, configuration, public method signatures, permissions, messages or Bukkit event annotations. A source comparison checked 60 moved method bodies against the originals, allowing only dependency qualifiers.

The 41 pre-existing tests passed before and after extraction. Temporary tests compared original and refactored implementations across 184 command scenarios (console/player, admin permissions, valid/invalid inputs), SQLite persistence/reset/restart behavior with both coin reset settings, and mission events/state cleanup/API annotations. All 237 tests passed together. Temporary sources, legacy comparison copies and their generated files are removed after validation; only the existing suite remains in the repository. A live Minecraft server and a live MySQL instance were not used.

The subsequent menu/reward extraction moved 11 method bodies unchanged into `BattlePassRewardRenderer`, `BattlePassMenuControls` and `RewardClaimHandler`. A source comparison verified those bodies against local original copies. Temporary comparisons checked 48 menu scenarios (pages, access/levels, claimed/hidden rewards, missing rewards, model data, navigation tags, admin/shop/daily buttons) and 22 claim scenarios (eligibility, cached/database claims, database errors, commands, configured/legacy/fallback item paths and full inventory). They compare rendered slots/metadata and delivery/messages/dirty marking/claim sets with the original classes. All 111 tests passed together, including the unchanged 41 existing tests. Temporary comparison tests, original copies and generated test artifacts are deleted before the final clean package. No live server was used for this extraction.

## Commit and push

The owner requires source changes to be pushed to `https://github.com/lino9999/BattlePass` after each completed task. Inspect `git status -sb`, the current branch and remote before editing. Run the relevant checks, review `git diff --check` and the diff, stage only task files, commit, and push. Verify the remote head matches the delivered commit. Existing checkout branch is normally `master`; newly needed branches use `codex/`.

Do not force-push, include unrelated edits, or commit generated JARs/credentials/server data. If authentication or branch protection blocks the push, keep the commit and explain the blocker. `.gitignore` controls Git; `.ignore` is for ripgrep-compatible search tools and does not promise exclusion by every AI client.
