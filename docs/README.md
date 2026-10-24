# AI agent quick reference

Read the matching guide, then inspect the named classes. Paths below are relative to the repository root. Java classes live under `src/main/java/com/Lino/battlePass/`; tests mirror that package under `src/test/java/`.

| Task | Read first | Entry points |
| --- | --- | --- |
| Mission not counting, food, target matching | [MISSIONS.md](MISSIONS.md) | `listeners/MissionProgressListener`, `models/Mission`, `managers/MissionProgressTracker` |
| Damage counting/overflow, projectiles or unavailable MythicMobs hook | [MISSIONS.md](MISSIONS.md) | `CombatMissionTracker.calculateDamageAmount`, `EventManager.registerMythicMobs` |
| XP grants, large level jumps, level feedback and actionbars | [EXPERIENCE.md](EXPERIENCE.md) | `ExperienceProgression`, `MissionProgressTracker`, `MissionActionbars`; four XP entry points |
| XP multiplier event startup, countdown, bossbar and vouchers | [EXPERIENCE.md](EXPERIENCE.md) | `XPEventManager`, `commands/XPEventCommands`, `CustomItemRedemptions.handleXPEventUse` |
| XP hook resets/quit, stale held items and dirty marking | [EXPERIENCE.md](EXPERIENCE.md), [ITEM_SOUNDS.md](ITEM_SOUNDS.md) | `PlayerData` runtime progress revision, `MissionProgressTracker`, `CustomItemRedemptions`, `HeldItemSnapshot` |
| Daily reward availability, claim reentrancy and timestamp limits | [EXPERIENCE.md](EXPERIENCE.md) | `DailyRewardCooldown`, `GuiClickListener.handleDailyRewardClaim`, `MissionResetHandler`, menu/placeholder readers |
| Daily missions, season dates, resets | [ARCHITECTURE.md](ARCHITECTURE.md) | `MissionManager`, `MissionResetHandler`, `SeasonRotationManager` |
| Reset command results, legacy APIs and play-time cycle boundaries | [RESET_WORKFLOW.md](RESET_WORKFLOW.md) | `MissionManager` async request methods, `SeasonAdminCommands`, `PlayTimeTracker` |
| Season reward rotation, archives and interrupted file updates | [SEASONS.md](SEASONS.md) | `SeasonRotationManager`, `SeasonArchiveInitializer`, `SeasonFileTransaction`, `AtomicFile` |
| Database, player cache, saving | [ARCHITECTURE.md](ARCHITECTURE.md) | `DatabaseManager` facade; `DatabaseConnections`, `PlayerDataRepository`, `SeasonDataRepository`, `DailyMissionRepository` |
| Commands and permissions | [ARCHITECTURE.md](ARCHITECTURE.md) | `BattlePassCommand` router; `PlayerAdminCommands`, `RewardAdminCommands`, `SeasonAdminCommands`, `XPEventCommands` |
| Menu input, titles, editors, pages, reload | [MENUS.md](MENUS.md) | `BaseHolder.MenuContext`; the three click listeners; `Pagination` |
| Editor drafts, chat, season files and YAML saves | [EDITORS.md](EDITORS.md) | `RewardEditorManager`, `RewardEditorStorage`, `MissionEditorManager`, `AtomicYamlFile` |
| Battle Pass rendering | [ARCHITECTURE.md](ARCHITECTURE.md) | `gui/BattlePassGui` facade; `BattlePassRewardRenderer` for reward icons/lore; `BattlePassMenuControls` for navigation/actions |
| Reward loading, serialization, archived previews or claims | [REWARDS.md](REWARDS.md) | `RewardConfiguration`, `ItemSerializer`, `RewardManager`, `RewardClaimHandler`, `models/Reward` snapshots |
| Periodic coin deliveries / saved deadline | [COINS.md](COINS.md) | `CoinsDistributionTask`, `CoinDistributionRepository`, `PlayerDataManager` preflight and `BattlePass` timer lifecycle |
| Periodic saves, reward notifications, cleanup and time sampling | [TASKS.md](TASKS.md) | `BattlePassTask`, `RewardNotifications`, `BattlePass.periodicTask` |
| Custom item redemption, feedback, held-item sounds and quit cleanup | [ITEM_SOUNDS.md](ITEM_SOUNDS.md) | `CustomItemsListener`, `CustomItemRedemptions`, `CustomItemEffects`, `SoundManager` |
| Update checks, HTTP cleanup and stale startup callbacks | [ARCHITECTURE.md](ARCHITECTURE.md) | `BattlePass.checkForUpdates`, `UpdateChecker`, `VersionUtils` |
| Login notifications, disconnect and final play-time flush | [ARCHITECTURE.md](ARCHITECTURE.md) | `PlayerConnectionListener.JoinNotification`, `EventManager.stopJoinFeedback`, `PlayTimeTracker` |
| PlaceholderAPI globals, percentages and cached leaderboard | [PLACEHOLDERS.md](PLACEHOLDERS.md) | `BattlePassExpansion`, `LeaderboardPlaceholders`; public names in root `Placeholders.md` |
| Leaderboard menu query failures, closed views and late SQL responses | [MENUS.md](MENUS.md) | `gui/LeaderboardGui`, `LeaderboardResults`; separate from the placeholder cache |
| Gradients, Unicode, formatting and locale-sensitive identifiers | [TEXT.md](TEXT.md) | `GradientColorParser`, `GradientText`, `ConfigManager`, command router/completer |
| YAML read/reload failures, GUI materials and startup validation | [CONFIGURATION.md](CONFIGURATION.md) | `ConfigurationFiles`, `ConfigManager`, `GuiConfiguration`, `MessageManager`, `ShopManager`, startup/reload recovery ordering |
| Other menus, custom items, coins | [ARCHITECTURE.md](ARCHITECTURE.md) | Use the feature table |
| Build, regression tests, push | [DEVELOPMENT.md](DEVELOPMENT.md) | `pom.xml`, `AGENTS.md` |
| Active bug/optimization audit | [CODE_AUDIT.md](CODE_AUDIT.md) | Verified fixes, pending investigations and test evidence |
| Previously investigated reports | [ISSUE_REVIEW.md](ISSUE_REVIEW.md) | Historical findings dated 2026-09-29; not live issue status |

Useful focused searches:

```powershell
rg -n 'EAT_ITEM|onPlayerConsume|isValidTarget' src/main/java
rg -n 'progressMission|trackProgress' src/main/java
rg -n 'setting-name' src/main/resources src/main/java
git status -sb
```

Canonical configuration examples are in `src/main/resources/`. `README.md` documents commands and permissions; `Placeholders.md` covers PlaceholderAPI. Avoid scanning `target/`, local IDE files or caches. Keep these guides concise and update them alongside relevant changes.
