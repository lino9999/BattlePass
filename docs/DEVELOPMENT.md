# Build, tests and delivery

## Toolchain

Use JDK 21 and Maven 3.9.x (`java -version`, `mvn -version`). There is no checked-in Maven wrapper. The project compiles against Spigot API `1.21.4-R0.1-SNAPSHOT` and declares `api-version: '1.21'`. Do not assume compatibility with older Minecraft versions.

```powershell
mvn '-Dtest=EatMissionTargetTest,EatMissionProgressTest' test
mvn clean package
```

The packaged plugin is `target/BattlePass-<version>.jar`; `target/original-*.jar` is the pre-shade artifact. The current version is in `pom.xml`. Source resources are filtered during packaging. Maven Shade may generate `dependency-reduced-pom.xml`; it is ignored.

First builds need network access to the repositories declared in `pom.xml`, including Spigot, PlaceholderAPI and Lumine. Download caches/tooling belong outside Git. Do not replace optional API dependencies with local stubs to make a failing build look successful.

## Focused test map

| Area | Tests (class names) |
| --- | --- |
| Food targets and consumption | `EatMissionTargetTest`, `EatMissionProgressTest` |
| Season countdown/reload/reset ordering | `MissionManagerSeasonTest`, `DatabaseSeasonDataTest` |
| Schema migration/preserved data | `DatabaseSchemaTest` |
| Coins and single task/deadline | `CoinsDistributionTaskTest`, `CoinsDistributionSchedulingTest` |
| Inventory ownership | `GuiIsolationTest` |
| XP cancellation | `BattlePassXPGainEventTest` |
| Config keys/reload | `ReportedConfigurationTest` |
| Version and descriptor | `VersionUtilsTest`, `PluginMetadataTest` |

JUnit Jupiter + Mockito are configured in `pom.xml`. SQLite tests use a real temporary database; MySQL uses mocked JDBC. Surefire excludes Mythic-Dist because its bundled SLF4J classes conflict with the test runtime. New listener tests can mock static `Bukkit` and the scheduler; use `RegisteredListener` when checking `ignoreCancelled`, since invoking the handler directly bypasses Bukkit's dispatch rules.

Automated tests do not replace a live Minecraft smoke test. Report exactly what was run and distinguish a reproduced failure from a suspected customer environment issue.

## Commit and push

The owner requires source changes to be pushed to `https://github.com/lino9999/BattlePass` after each completed task. Inspect `git status -sb`, the current branch and remote before editing. Run the relevant checks, review `git diff --check` and the diff, stage only task files, commit, and push. Verify the remote head matches the delivered commit. Existing checkout branch is normally `master`; newly needed branches use `codex/`.

Do not force-push, include unrelated edits, or commit generated JARs/credentials/server data. If authentication or branch protection blocks the push, keep the commit and explain the blocker. `.gitignore` controls Git; `.ignore` is for ripgrep-compatible search tools and does not promise exclusion by every AI client.
