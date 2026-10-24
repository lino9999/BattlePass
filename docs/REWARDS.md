# Reward loading, previews and delivery

Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Shared active/archive definitions | `utils/RewardConfiguration.readRuntimeRewards` |
| Live maps, maximum level and publication | `managers/RewardManager.loadRewards` |
| Item decoding/encoding and usable-item checks | `utils/ItemSerializer` |
| Owned item snapshot and legacy constructors | `models/Reward` |
| Claim eligibility, preparation and delivery | `managers/RewardClaimHandler` |
| Archived category labels/read errors | `gui/RewardsCategoryGui.loadSeasonRewards` |
| Safe main-menu material icons | `gui/BattlePassRewardRenderer.createIcon` |
| Strict interactive drafts and persistence | [EDITORS.md](EDITORS.md) |

Runtime and archived previews share the same parser. Visit canonical positive `level-N` keys only; ignore noncanonical/overflowing keys rather than scanning through the maximum. Keep configured empty levels in the parser result for editor pagination. Live maps publish nonempty lists only and retain a minimum maximum level of 54. Prepare both free/premium candidates before replacing the live maps. Existing public maps/lists remain mutable compatibility APIs.

A single command/material/serialized-item definition takes precedence over `items`; commands take precedence over item fields. Multiple entries preserve their configured order. Serialized-only definitions work at either level. Runtime parsing warns/skips an unreadable entry or empty command while retaining valid neighbors. Archived YAML uses ConfigurationFiles.read, so syntax/read failures report an unavailable archive instead of displaying an empty successful preview. Opening a strict editor still rejects a partially unreadable level rather than allowing a save to delete it.

ItemSerializer reads the full serialized item first. Validate its type, positive quantity and usable non-air item material; close streams on success/failure. The existing lenient reader may fall back to valid simple material/metadata fields after a bad serialized payload. The strict editor reader rejects the bad payload even when fallback fields exist. Simple fields retain material case normalization, amount clamping, display-name/lore, enchantments, flags, model data and unbreakable handling. `display` is the reward label; `display-name` belongs to the delivered item.

Loaded Reward objects own a cloned item snapshot. Their material/amount and default label derive from the actual decoded item, including serialized data that differs from duplicated simple fields. getItemStack returns a fresh clone for each delivery. A raw YAML edit takes effect after reward reload; an inventory mutating its input cannot alter the shared definition. This also avoids decoding the same serialized item at every claim. Existing public constructors/fields remain; legacy Reward instances without a snapshot keep their configurationPath lookup and older fallback behavior. New internally loaded definitions never rediscover an entry by matching material.

Claims retain level/premium, cached claim and persisted claim checks. Prepare every item and copy the reward list before marking the level claimed or executing any commands. A preparation error leaves the claim retryable and sends the existing failure feedback without delivering earlier commands. Once prepared, add/mark the claim dirty before commands, inventory work or feedback can save/disconnect the player. Synchronizing an already persisted claim into the cache also marks before presentation. Preserve command order, `<player>` replacement, command-error logging, cloned item delivery and dropping inventory leftovers.

This is preparation plus early dirty tracking, not an atomic transaction across commands, inventories and SQL. Command side effects/delivery failures are not rolled back. Do not remove claim marking on a late failure after some rewards may already have been delivered.

Main-menu reward/control icons validate materials at rendering, where Bukkit registries are available. Air/non-item materials use each control's existing default, warning once per path per renderer. This covers available, no-pass, locked, claimed and separator icons. Claimed NONE/null still hides the icon; model-data application stays unchanged. This validation does not rewrite the user's configuration.

The 2026-10-24 verification passed 72 tests, including the unchanged 41 existing tests and 31 temporary reward cases. Tests cover active/archive parsing, serialized-only and mismatched fallback fields, metadata paths, null/wrong serialized objects, stream closure, legacy fallback, snapshot copies, sparse levels, staged publication, dirty timing, command order and invalid/hidden GUI icons. Bukkit registries, metadata, inventory and some stream reads are mocked; plain ItemStack cloning and a real serialized-null payload are also exercised. No live Minecraft server/full platform metadata round trip was used. Temporary sources/artifacts are removed before delivery.
