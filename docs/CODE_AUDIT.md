# Code audit — started 2026-10-23

Status: in progress. This is a source review and regression-test record, not a claim that every possible server/plugin interaction has been proven safe. Keep the existing local deletion of `CHANGELOG.md` out of audit commits.

## Verified fixes: player persistence and menu input

- Player writes now acknowledge the exact dirty revision captured by the snapshot. Changes made while that write is pending remain dirty and are saved on the next pass.
- Autosave and quit no longer submit overlapping writes for the same player. Synchronous flush waits for a pending write, then saves any newer revision. Failed writes remain eligible for retry. Autosave visits dirty IDs rather than copying all cached players.
- Duplicate player loads are coalesced. Cache clearing invalidates pending loads, preventing an old load from restoring pre-reset data or replacing a newer loaded instance. Failed loads allow retries.
- Main menus and mission editors only activate buttons from the top inventory. Clicks in the player's own inventory stay cancelled where required but no longer invoke menu actions.
- Dragging into read-only main menus, mission editors and reward category menus is cancelled. Bottom-only drags and foreign inventories remain unaffected; the reward item editor keeps its existing item-copy workflow.

Validation: the initial 10 temporary regression checks reproduced six failures in the old code. After the fixes, all 52 tests passed (41 pre-existing plus 11 temporary tests; the drag test covers 12 combinations). Temporary test sources/classes/reports are removed before delivery as required by the owner.

## Remaining review work

The audit is still active. Inspect and reproduce these candidates before changing behavior:

- `PlayerDataManager.clearCache(true)`: what happens when the flush fails? Ensure season reset cannot discard unsaved state.
- `PlayerDataRepository`: mission-date lookup while holding a pooled MySQL connection; check behavior with pool size one.
- `RewardManager`: multiple reward items sharing a material select the first matching configuration entry; plain YAML item metadata and duplicate claim guards.
- `ShopManager`: reusable item templates passed directly to inventory insertion; check repeated purchases when the insertion mutates its argument.
- `XPEventManager.parseDuration`: oversized numbers, arithmetic overflow and malformed input reaching admin commands.
- Mission generation/progress and XP/coin arithmetic: integer bounds and invalid configuration.
- Crafting/trading progress: actual items taken versus potential output, shift clicks and full inventories.
- Playtime/world/teleport state, season reset/save ordering, async coin distribution, startup/shutdown/reload, editors, GUI caches, serialization, placeholders, configuration and optional integrations.

Review every production source file and relevant resource; track further findings and verification here. A live Minecraft or MySQL server has not been exercised so far.
