# Menu identity, routing and pagination

Use this guide for clicks, drags, navigation, menu reloads or editable reward previews. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Change | Read first |
| --- | --- |
| Inventory identity/context | `gui/BaseHolder`, `BaseGui` |
| Main menu actions and reward claims | `listeners/GuiClickListener` |
| Reward category/level editing and closing sessions | `listeners/RewardsEditorListener`, `gui/RewardsCategoryGui`, `LevelRewardEditGui` |
| Mission list/type/details editing | `listeners/MissionEditorListener`, `gui/MissionEditorGui`, `MissionTypeSelectionGui`, `MissionDetailsGui` |
| Main menu rebuilding on reload | `BattlePass.reload`; `GuiManager` keeps navigation history |
| Sparse reward definitions and shared archived previews | `utils/RewardConfiguration.readRuntimeRewards`, `managers/RewardManager.loadRewards` |
| Page bounds and final integer level | `utils/Pagination` |

Every built-in menu has an immutable `BaseHolder.MenuContext`. Its `MenuType`, displayed page, premium flag, mission key/slot entries and active level-editor object determine actions. Titles, item labels and translation prefixes are presentation only. Do not restore `startsWith`/title substring routing or parse page numbers from titles. All ten GUI builders attach their context; inherited builders override `BaseGui.getMenuContext`, static builders pass context to the three-argument `BaseGui.createInventory` overload.

Existing generic constructors/factories remain available and create UNKNOWN context. UNKNOWN and foreign holders do not activate built-in actions. Integrations that intentionally create a BattlePass action menu must supply its context. Inventory-holder ownership is still checked before altering input.

Main menu handlers act only on top raw slots. Each listener cancels top drags only for its own menu types; bottom-only/foreign drags remain independent. Reward rows are checked before material-based buttons. The level editor preserves bottom-inventory item copying, checks that the displayed editor object and page are still active and does not clear cancellations from other handlers. Delayed close cleanup also compares the editor object, so an old close cannot remove a newer session. Editor actions recheck admin permission; pending chat stays private. Input tokens are consumed on permission loss without applying changes or reopening an admin menu. See [EDITORS.md](EDITORS.md) for draft and persistence rules.

Reload rebuilds only owned main menus using their context, preserves navigation history, and avoids scanning every possible reward page. The delayed reopen checks that the player is online and has not opened another inventory in the meantime. Leaderboard callbacks refresh only the exact inventory they populated, irrespective of its title.

LeaderboardGui delegates its asynchronous result to package-private LeaderboardResults. Each menu open retains its fresh query; the GUI does not use the PlaceholderAPI five-second cache or require cached viewing-player data. Capture the database instance and discard stopped/disabled/replaced-manager responses before scheduling and on execution. Worker completion copies at most ten PlayerData DTOs and only schedules server-thread work; player/inventory/profile/skull APIs stay on the server thread. Check the exact still-open inventory before resolving profiles, between rows and before inventory writes/refresh. A closed or replaced view must not receive an old result or late failure feedback.

Do not test inventory ownership before queueing the worker result: an already completed SQL future can run inline before open() has displayed the new inventory. Check view identity in the queued server-thread callback. Query/submission/render failures log and report messages.leaderboard-unavailable only to the current viewer; scheduler rejection logs only while the request's lifecycle is active. Preserve existing slots, rank/name/statistic lore, Unknown fallback and inventory refresh behavior. Packaged defaults supply the new failure translation in memory without rewriting custom messages.

Reward loading visits configured canonical positive `level-N` keys rather than every number through the maximum. Item order, command precedence, stored configuration paths and the minimum maximum-level value of 54 stay intact. Malformed `items` sections are skipped with a warning. Material/enchantment identifiers use `Locale.ROOT`, including item deserialization; custom display text remains unchanged.

Active/archive labels and item definitions now use the same parser, including serialized-only entries and `display` labels. Reward objects retain cloned loaded items, and malformed archive YAML reports failure instead of an empty preview. Main-menu reward icons fall back to their existing defaults for air/non-item materials, while claimed NONE remains hidden. See REWARDS.md for parsing and delivery details.

Use `Pagination` for page counts, clamping and cell-to-level conversion. `levelAt` returns zero for a nonrepresentable cell; do not render or activate it. Reward category editors retain their extra page for adding levels, cap at `Integer.MAX_VALUE`, and use the selected archived season's own levels. Mission-list context records raw configured keys so colored labels cannot change the selected pool. Non-section mission pools are omitted from the editor.

Shop slots 4 (balance) and 49 (back) are reserved. The loader warns and skips entries configured there; other slots/materials retain their normal purchasing behavior. Disabled shops reject purchases from already open inventories, and opening a disabled shop returns to the last Battle Pass page. See CODE_AUDIT for test evidence.
