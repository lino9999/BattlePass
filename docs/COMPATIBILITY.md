# Minecraft Java compatibility (verified 2026-10-24)

Target: Spigot and available Paper releases from 1.21.11 through 26.3. Build the release against Spigot 1.21.11 with Java `release=21`; run 1.21.11 on Java 21 and 26.x on Java 25. The descriptor declares minimum API 1.21.11. [Paper's Java requirements](https://docs.papermc.io/paper/getting-started/) and the [official game manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) are the external references.

## Where to change things

| Concern | Entry points / invariant |
| --- | --- |
| Minimum API, compiler target, optional matrix | `pom.xml` server.api properties and paper-api profile; provided API only |
| Bukkit minimum and material remapping | `src/main/resources/plugin.yml` api-version |
| Configured material spelling | `models/Mission.matchesTarget`: resolve with the running server's Material.matchMaterial; never rewrite saved fields/keys |
| All break/place targets | `listeners/BlockMissionTracker`: actual event block Material.name; no fixed catalog |
| Mining classification | `BlockMissionTracker.initializeOreTypes`: *_ORE, ANCIENT_DEBRIS, GILDED_BLACKSTONE; retains drop requirement |
| Reward serialization | `utils/ItemSerializer`: standard Java Base64 around unchanged Bukkit object streams |

BREAK_BLOCK and PLACE_BLOCK use the complete material catalog exposed by that server. Namespaced, canonical, lowercase and spaced names, including additional targets, work for the eight material mission types. Action eligibility remains specific to each type: food must be consumed, MINE_BLOCK requires an ore with drops, and crafting/fishing/etc. require their normal events. No newer block is backported onto an older server. A shared pool must select targets that exist on every participating server or provide usable alternatives. Custom datapack/mod blocks without their own Bukkit Material are outside this guarantee. See [MISSIONS.md](MISSIONS.md).

## API matrix

Every row passed the 41 existing tests plus 14 temporary compatibility checks. The latter iterate every nonlegacy material across all eight material mission types, five configured spellings and primary/additional targets, preserve raw targets and custom identifiers, assert the actual API classpath, and check the Base64 codec. Spigot 26.x skips two legacy-codec interoperability checks because that old class is absent; those checks run successfully on the minimum Spigot API and all five Paper APIs. No test failures/errors occurred after correction.

| Minecraft | Materials (nonlegacy) | Spigot API snapshot | Paper API / download build |
| --- | ---: | --- | --- |
| 1.21.11 | 1,658 | 1.21.11-R0.1-20251231.154832-9 | 1.21.11-R0.1-SNAPSHOT / 132 stable |
| 26.1 | 1,660 | 26.1-R0.1-20260329.091546-5 | No published build |
| 26.1.1 | 1,660 | 26.1.1-R0.1-20260408.091146-5 | 26.1.1.build.29-alpha |
| 26.1.2 | 1,660 | 26.1.2-R0.1-20260616.152059-13 | 26.1.2.build.74-stable |
| 26.2 | 1,691 | 26.2-R0.1-20260915.151451-13 | 26.2.build.134-stable |
| 26.3 | 1,815 | 26.3-R0.1-20261004.024323-9 | 26.3.build.218-stable |

Paper availability/channels come from the [official downloads service](https://fill.papermc.io/v3/projects/paper). Its 26.1 group contains 26.1.1/26.1.2, with no 26.1 release build. Do not describe an untested nonexistent Paper 26.1 artifact as verified. Exact checksums were verified locally against official metadata; downloaded tools and servers stay outside the repository.

## Live server checks

A temporary probe loads alongside the plugin on disposable localhost-only servers, waits for normal startup/SQLite initialization, then dispatches registered block break/place and golden-apple consumption events through Bukkit. Player and event block inputs are proxies; the plugin's managers, progress tracker, cancellation dispatch, material registries, item factory, serialization, scheduler and database are real. This is a server smoke test, not a connected-client gameplay test or optional-plugin integration test. It checks both successful and cancelled block events for every nonlegacy Material.isBlock entry (including technical/air blocks that players cannot normally place).

| Server | Startup/progress/serialization | Blocks | Plain item round trips | Events dispatched |
| --- | --- | ---: | ---: | ---: |
| Paper 1.21.11 build 132 | Passed | 1,166 | 1,504 | 4,666 |
| Paper 26.1.1 build 29 alpha | Passed | 1,168 | 1,505 | 4,674 |
| Paper 26.1.2 build 74 | Passed | 1,168 | 1,505 | 4,674 |
| Paper 26.2 build 134 | Passed | 1,196 | 1,536 | 4,786 |
| Paper 26.3 build 218 | Passed | 1,286 | 1,657 | 5,146 |
| Spigot 1.21.11 build 4598 | Passed | 1,166 | 1,504 | 4,666 |
| Spigot 26.1.2 build 4632 | Passed | 1,168 | 1,505 | 4,674 |
| Spigot 26.2 build 4648 | Passed | 1,196 | 1,536 | 4,786 |
| Spigot 26.3 build 4663 | Passed | 1,286 | 1,657 | 5,146 |

BuildTools requests for 26.1 and 26.1.1 currently resolve to the same 26.1.2 build 4632; their distinct historical APIs were tested, but these are not additional live server versions. Compare the official [26.1 refs](https://hub.spigotmc.org/versions/26.1.json) and [26.1.1 refs](https://hub.spigotmc.org/versions/26.1.1.json). Some 26.x server startup logs report unavailable Windows performance counters from OSHI; the probes still pass and no BattlePass exception is involved.

## Reproduced incompatibility and delivery rules

MissionEditorManager uppercases entered targets, including a valid minecraft: namespace. Bukkit Material.matchMaterial strips that prefix case-sensitively; consequently the saved MINECRAFT:DIRT and newer block equivalents failed. Eight catalog regressions reproduced the problem before correction. Mission now lowercases material lookup inputs with Locale.ROOT at comparison time, preserving stored fields/progress keys and leaving nonmaterial/custom-mob matching unchanged.

The former SnakeYAML internal Base64Coder import compiles on the minimum API but fails on Spigot 26.x because the class was removed. Use java.util.Base64 with 76-column wrapping, legacy CR/LF/tab/space tolerance and padded-length/invalid-character validation. Tests cover old encoder -> new decoder, new encoder -> old decoder where available, randomized payload lengths, wrapping and malformed inputs. The serialized-item field and Bukkit object payload are unchanged; Minecraft metadata downgrade across versions is not guaranteed.

Each API variant was clean-compiled (all 97 production sources) before running the tests. Final clean packaging uses the minimum API and JDK 21, passes the unchanged 41 existing tests, and is built from an isolated export of the staged delivery tree to exclude pre-existing unrelated workspace edits. The same resulting JAR is checked on all nine live server variants above. Temporary test/probe sources, classes, reports and disposable server data are deleted locally before committing, and none are published. The built release JAR stays local. See [DEVELOPMENT.md](DEVELOPMENT.md) for the API selection commands and owner delivery policy.
