# Architecture and change map

All Java paths below are relative to `src/main/java/com/Lino/battlePass/`.

## Lifecycle and data

`BattlePass.onEnable` creates managers, initializes the database asynchronously, then initializes missions on the Bukkit thread. Once missions are ready it loads online players, registers listeners/commands, starts scheduled work, and registers optional integrations. `listeners/EventManager` owns listener registration; MythicMobs is conditional.

`models/PlayerData` is the cached mutable player state. `managers/PlayerDataManager` loads it, tracks dirty players via `markForSave(UUID)`, and saves snapshots. Changes to cached XP, coins, rewards or mission progress must be marked dirty. `managers/DatabaseManager` handles SQLite/MySQL and asynchronous persistence; `DatabaseSchema` handles additive migrations. Do not remove existing player data to fix a schema mismatch.

`BattlePass.onDisable` stops XP events/sounds/coin work, saves players synchronously, shuts down missions and closes the database. Bukkit world, player and inventory operations belong on the server thread; database callbacks often schedule work back onto it.

## Feature map

| Feature | Main files | Related resources |
| --- | --- | --- |
| Missions and progress | `listeners/MissionProgressListener`, `managers/MissionManager`, `MissionGenerator`, `MissionProgressTracker`, `models/Mission` | `missions.yml`, `config.yml` |
| Daily and season deadlines | `MissionResetHandler`, `SeasonRotationManager`, `MissionManager` | `config.yml`; saved season start/end and mission date |
| Periodic coins | `tasks/CoinsDistributionTask`, `BattlePass.startCoinsDistributionTask` | `config.yml`; saved next-distribution deadline |
| Rewards and claims | `managers/RewardManager`, `models/Reward`, `gui/BattlePassGui` | `BattlePassFREE.yml`, `BattlePassPREMIUM.yml` |
| Custom vouchers/tokens/elixirs | `managers/CustomItemManager`, `listeners/CustomItemsListener` | PersistentDataContainer keys, `messages.yml` |
| Shop | `managers/ShopManager`, `gui/ShopGui`, `models/ShopItem` | `shop.yml` |
| Mission editing | `managers/MissionEditorManager`, `listeners/MissionEditorListener`, `gui/Mission*Gui` | Edits `missions.yml` pools |
| Reward editing | `managers/RewardEditorManager`, `listeners/RewardsEditorListener`, editor GUIs | Reward configuration |
| Menu input isolation | `gui/BaseHolder`, `listeners/GuiClickListener`, editor listeners | Inventory ownership, not title alone |
| Commands and completion | `commands/BattlePassCommand`, `BattlePassTabCompleter` | `plugin.yml` permissions/aliases |
| Settings/messages/colors | `managers/ConfigManager`, `MessageManager`, `utils/GradientColorParser` | `config.yml`, `messages.yml` |
| Integrations/API | `placeholders/BattlePassExpansion`, `listeners/MythicMobsListener`, `events/BattlePassXPGainEvent` | `Placeholders.md`; optional plugins |

## Invariants worth preserving

- Season duration is counted from the persisted season start. Reload/restart must not restart the countdown. Legacy duration seasons without a saved start get a one-time new countdown.
- Saves must not let an older season deadline overwrite a newer one. New missions are saved after season-reset deletion completes.
- Coin distribution has one managed task. Rescheduling/manual season resets preserve an existing delivery deadline.
- XP grants fire `BattlePassXPGainEvent`; cancellation and modified nonnegative amounts are respected.
- Custom item identity comes from persistent tags, not material/name alone. Redeeming a stack must consume the same quantity used for the reward and the correct hand.
- GUI handlers check `BaseHolder.isBattlePassInventory` before cancelling or altering another inventory.
- Version comes from `pom.xml` and is filtered into `plugin.yml`. Do not hardcode a second descriptor version.
