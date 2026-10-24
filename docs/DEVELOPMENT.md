# Build, tests and delivery

## Toolchain

Use JDK 21 and Maven 3.9.x (`java -version`, `mvn -version`) for the release build. There is no checked-in Maven wrapper. The project compiles against Spigot API `1.21.11-R0.1-SNAPSHOT`, declares `api-version: '1.21.11'` and uses compiler `release=21`. Use JDK 25 for the 26.x API matrix and servers; the output remains Java 21 bytecode. See [COMPATIBILITY.md](COMPATIBILITY.md) for the Paper/Spigot 1.21.11–26.3 matrix and material guarantees. Do not compile the distributed JAR against the newest API: that can introduce calls absent from the minimum server.

```powershell
mvn test
mvn clean package
```

Optional API checks use `mvn -Dserver.api.version=26.3-R0.1-SNAPSHOT clean test` for Spigot or `mvn -Ppaper-api -Dserver.api.version=26.3.build.218-stable clean test` for Paper, with JDK 25. Paper's default profile version is the same minimum 1.21.11 snapshot. Clean between variants so incremental compilation cannot reuse classes from another API/compiler. These change the provided server dependency only; return to the default profile for the final clean package. Never shade a server API into the plugin.

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

## GUI configuration and custom-item extraction (2026-10-24)

ConfigManager now delegates its GUI material/model/visibility state to package-private GuiConfiguration (348 to 265 lines). CustomItemsListener delegates synchronous redemptions to package-private CustomItemRedemptions (326 to 114 lines); the listener retains event annotations, priority, recognition order, hand eligibility, cooldown and lifecycle, sharing its original effects instance. Public APIs, configuration paths/defaults, item quantities, XP hooks, feedback ordering and task delays are unchanged. A source comparison verified all 20 moved method bodies and the GUI reload block exactly; interaction dispatch differs only in helper qualifiers.

The 41 existing tests pass before and after extraction. Temporary comparisons run original and extracted classes through 12 configuration scenarios, including strings/sections/NONE/invalid values, model-data cleanup, all public getters, reload failure preservation and recovery; 60 custom-item scenarios cover both hands, premium/coin/boost/event items, cancellation/modified XP, wallet limits, missing data, rejected starts, cooldown, quit/retry, coalesced inventory checks and shutdown effects. One additional comparison checks public listener methods and Bukkit annotations. All 114 tests passed together. Temporary comparison tests and original copies are removed, followed by clean package with the unchanged 41 existing tests. Bukkit, scheduling and sounds are mocked; no live Minecraft server was used.

## Commit and push

Public binaries belong in GitHub Releases as assets, not in the Git tree. Version 8.6.1 uses tag v8.6.1 and asset BattlePass-8.6.1.jar; preserve the historical Release-tagged 8.6 download. Keep the Maven/packaged descriptor version, README download name and plain-English docs/RELEASE_NOTES.md consistent. Build from the intended delivery tree when the workspace contains unrelated source edits, verify the asset checksum after uploading, and publish the release after its JAR is attached. Preserve existing repository topics when adding relevant ones.

The owner requires source changes to be pushed to `https://github.com/lino9999/BattlePass` after each completed task. Inspect `git status -sb`, the current branch and remote before editing. Run the relevant checks, review `git diff --check` and the diff, stage only task files, commit, and push. Verify the remote head matches the delivered commit. Existing checkout branch is normally `master`; newly needed branches use `codex/`.

Do not force-push, include unrelated edits, or commit generated JARs/credentials/server data. If authentication or branch protection blocks the push, keep the commit and explain the blocker. `.gitignore` controls Git; `.ignore` is for ripgrep-compatible search tools and does not promise exclusion by every AI client.
