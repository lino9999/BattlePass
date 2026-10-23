# AI agent quick reference

Read the matching guide, then inspect the named classes. Paths below are relative to the repository root. Java classes live under `src/main/java/com/Lino/battlePass/`; tests mirror that package under `src/test/java/`.

| Task | Read first | Entry points |
| --- | --- | --- |
| Mission not counting, food, target matching | [MISSIONS.md](MISSIONS.md) | `listeners/MissionProgressListener`, `models/Mission`, `managers/MissionProgressTracker` |
| Daily missions, season dates, resets | [ARCHITECTURE.md](ARCHITECTURE.md) | `MissionManager`, `MissionResetHandler`, `SeasonRotationManager` |
| Database, player cache, saving | [ARCHITECTURE.md](ARCHITECTURE.md) | `DatabaseManager` facade; `DatabaseConnections`, `PlayerDataRepository`, `SeasonDataRepository`, `DailyMissionRepository` |
| Commands and permissions | [ARCHITECTURE.md](ARCHITECTURE.md) | `BattlePassCommand` router; `PlayerAdminCommands`, `RewardAdminCommands`, `SeasonAdminCommands`, `XPEventCommands` |
| Menus, rewards, custom items, coins | [ARCHITECTURE.md](ARCHITECTURE.md) | Use the feature table |
| Build, regression tests, push | [DEVELOPMENT.md](DEVELOPMENT.md) | `pom.xml`, `AGENTS.md` |
| Previously investigated reports | [ISSUE_REVIEW.md](ISSUE_REVIEW.md) | Historical findings dated 2026-09-29; not live issue status |

Useful focused searches:

```powershell
rg -n 'EAT_ITEM|onPlayerConsume|isValidTarget' src/main/java
rg -n 'progressMission|trackProgress' src/main/java
rg -n 'setting-name' src/main/resources src/main/java
git status -sb
```

Canonical configuration examples are in `src/main/resources/`. `README.md` documents commands and permissions; `Placeholders.md` covers PlaceholderAPI. Avoid scanning `target/`, local IDE files or caches. Keep these guides concise and update them alongside relevant changes.
