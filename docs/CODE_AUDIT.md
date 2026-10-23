# Code audit — started 2026-10-23

Status: in progress. This is a source review and regression-test record, not a claim that every possible server/plugin interaction has been proven safe. Keep the existing local deletion of `CHANGELOG.md` out of audit commits.

## Verified fixes: player persistence and menu input

- Player writes now acknowledge the exact dirty revision captured by the snapshot. Changes made while that write is pending remain dirty and are saved on the next pass.
- Autosave and quit no longer submit overlapping writes for the same player. Synchronous flush waits for a pending write, then saves any newer revision. Failed writes remain eligible for retry. Autosave visits dirty IDs rather than copying all cached players.
- Duplicate player loads are coalesced. Cache clearing invalidates pending loads, preventing an old load from restoring pre-reset data or replacing a newer loaded instance. Failed loads allow retries.
- Main menus and mission editors only activate buttons from the top inventory. Clicks in the player's own inventory stay cancelled where required but no longer invoke menu actions.
- Dragging into read-only main menus, mission editors and reward category menus is cancelled. Bottom-only drags and foreign inventories remain unaffected; the reward item editor keeps its existing item-copy workflow.

Validation: the initial 10 temporary regression checks reproduced six failures in the old code. After the fixes, all 52 tests passed (41 pre-existing plus 11 temporary tests; the drag test covers 12 combinations). Temporary test sources/classes/reports are removed before delivery as required by the owner.

## Verified fixes: failed flush and single-connection pools

- `clearCache(true)` retains the cache and reports failure if revisions remain unsaved after flushing. An explicit `clearCache(false)` still discards state intentionally.
- Season reset performs that flush before rotating configuration or changing deadlines. A failed flush postpones the reset and preserves player state for retry.
- Player reads/writes resolve the current mission date before borrowing their player connection. The date lookup uses its own lease, so MySQL pool size one no longer causes nested connection acquisition and timeouts.

Validation: four temporary boundary tests cover failed flush/retry, reset preflight and read/write connection acquisition order. All 45 tests passed together; MySQL lease behavior was verified through mocked JDBC/connection ownership, without a live MySQL server.

## Verified fixes: rewards, shop templates and XP event input

- Loaded item rewards retain their exact configuration path. Two items of the same material no longer both resolve to the first entry, and a single plain YAML item also goes through the metadata loader. Existing Reward constructors remain available.
- Reward claiming checks cached claims, level eligibility and premium ownership at the manager boundary. Direct/repeated calls cannot bypass those checks while a database save is pending. Reward messages retain the original delivered amount even if inventory insertion mutates the stack.
- Shop insertion receives a cloned item stack, keeping the configured template intact across repeated purchases.
- XP duration parsing accepts complete h/m/s sequences (case-insensitive, whitespace allowed), rejecting malformed/overflowing values without exceptions. API starts validate multiplier, duration and end-time overflow before touching a running event; the command handles a rejected start.
- Replacing an expired XP event also removes its bar/task, even when its scheduled expiration tick has not run yet.

Validation: 23 temporary tests cover distinct same-material rewards, metadata-loader selection, duplicate/premium claims, repeated purchases with a mutating inventory, duration formats/overflow, API rejection and expired-event cleanup. All 64 tests passed together; temporary tests are removed before delivery. Bukkit registries, inventory insertion and item deserialization were mocked; no live server was used.

## Verified fixes: menu materials, mission bounds and cache lock ordering

- Reward rows are handled before material-based action buttons. Available/locked reward icons may share materials with books, navigation arrows, gold, sunflowers or command blocks without becoming unrelated controls. Action/back buttons also require their intended slots; a shop item using BARRIER outside the back slot remains purchasable.
- Mission ranges use a long upper bound so `Integer.MAX_VALUE` remains a valid inclusive maximum. Weight totals/selection use long arithmetic. Positive progress increments use long arithmetic before clamping to the requirement, preventing wraparound to zero.
- The earlier cache-generation fix exposed a lock-order issue: a synchronous cache clear could hold the cache monitor while waiting for a write behind a load whose completion needed that monitor. The flush now runs outside the monitor; generation checking and clearing remain inside it. A controlled single-thread database executor reproduced the timeout before correction.

Validation: 12 temporary tests cover six reward-icon material collisions, a purchasable barrier shop item, maximum mission bounds, overflowing progress, controlled load/write lock ordering, failed-flush retention and stale-load invalidation. All 53 tests passed together. Temporary tests are removed before delivery.

## Verified fixes: experience and currency arithmetic

- `ExperienceProgression` centralizes XP accumulation for missions, admin grants, daily rewards and elixirs. A long accumulator preserves the full sum through level conversion; callback feedback still runs once per gained level. Stored XP and lifetime level counts cannot wrap negative at the integer limit.
- Mission XP multipliers/elixir stack multiplication widen before conversion to the int-valued XP event API. Admin XP removal uses a long total and direct level/remainder calculation rather than an overflow-prone total and a level-by-level subtraction loop.
- `PlayerData.addBattleCoins` caps the persisted balance at `Integer.MAX_VALUE` and returns the actual credited amount. Commands/distribution report that amount. Coin tokens consume only the amount that fits; a full wallet keeps the tokens. Negative configured coin distributions are clamped to zero.
- MessageManager reads packaged defaults for missing messages while retaining custom values and leaving the server file untouched; this also makes the new full-wallet message available to existing installations.

Validation: nine boundary acceptance tests failed before correction. Fourteen temporary tests now cover the command/mission/daily/elixir XP paths, lifetime levels, normal gains, XP cancellation, full/partial token redemption, negative distribution configuration and translation fallback. One test compares 512 deterministic XP cases to an independent long-arithmetic oracle, including callback order. All 55 tests passed together. Temporary tests are removed before delivery. The database/API storage types remain int; amounts beyond representable stored capacity are capped instead of wrapping.

## Remaining review work

The audit is still active. Inspect and reproduce these candidates before changing behavior:

- Extreme reward configuration and progression scalability (very sparse/high levels); verify no loops scale with untrusted numeric ranges unnecessarily.
- GUI title collisions (including titles starting with a placeholder), reserved shop slots, item preview paths, pagination and malformed reward configuration.
- Repository exception propagation and reset/save barriers: avoid reporting failed writes/resets as successful, including MySQL concurrency and daily resets on the same calendar date.
- Crafting/trading progress: actual items taken versus potential output, shift clicks and full inventories.
- Playtime/world/teleport state, season reset/save ordering, async coin distribution, startup/shutdown/reload, editors, GUI caches, serialization, placeholders, configuration and optional integrations.

Review every production source file and relevant resource; track further findings and verification here. A live Minecraft or MySQL server has not been exercised so far.
