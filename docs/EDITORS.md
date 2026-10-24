# Reward and mission editing

Read this guide for editor drafts, chat input, selected seasons, serialization or YAML writes. Paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Reward session, chat and applying saved files | `managers/RewardEditorManager` |
| Bound reward file, optimistic revision and serialization | `managers/RewardEditorStorage` |
| Editable reward parsing | `utils/RewardConfiguration` |
| Strict YAML reads and staged writes | `utils/AtomicYamlFile` |
| Draft rendering and reward pages | `gui/LevelRewardEditGui`, `listeners/RewardsEditorListener` |
| Mission fields, input revision and saving | `managers/MissionEditorManager`, `listeners/MissionEditorListener` |
| Disconnect cleanup | `listeners/PlayerConnectionListener` |

Reward sessions capture an `EditorSnapshot`: canonical source file, season, level signature and loaded rewards. An explicitly selected season uses its archive. With rotation enabled, the default editor binds the current season archive because reload copies that archive into the live files. Without rotation it binds the main free/premium file. A later season selection or rotation cannot redirect an existing draft's save.

Archived draft/category loading calls `SeasonRotationManager.prepareSeasonArchive` first. Large default collections are initialized in the background, so a requested pending default must publish its whole free/premium pair before a draft/preview reads it. Preparation failures retain the current view/context and report an error; existing partial archives are preserved rather than silently filled. See `SEASONS.md` for job ownership and recovery ordering.

Interactive saves use `saveEditor`, not the programmatic `saveRewards` convenience method. Re-read the source and compare the edited level's signature before writing. Reject a conflict in that level, retain unrelated levels, and acknowledge the new signature only after the file is written. Inactive archives do not reload the live rewards. A reload error after persistence is reported separately and keeps the editor open.

`RewardEditorStorage` owns encoding; managers own permissions, messages and reloads. `RewardConfiguration` retains command precedence, single and multiple item formats, separate item display labels and the existing lenient public reader. Editors use strict parsing: corrupt serialized data or an unreadable entry must prevent opening a partial draft that could delete the original on save.

ItemSerializer also rejects serialized null/non-item/empty stacks and unusable simple materials. Active maps and archived category previews use RewardConfiguration.readRuntimeRewards, sharing item decoding, labels and precedence; strict drafts still reject malformed entries instead of skipping them. See REWARDS.md for loaded item snapshots and fallback limits.

`AtomicYamlFile` propagates invalid YAML/read failures, preserves parsed comments, writes and flushes a temporary sibling, then replaces the destination. Atomic rename is used where supported, with a replacement fallback. This is one-file persistence, not a transaction across season archives, live files and SQL. YAML formatting may normalize; editing a level regenerates its reward entries. Do not claim preservation of arbitrary unknown fields within rewritten entries.

Reward pages show 36 draft entries with controls at 47/51. Context captures both the editor object and displayed page. Convert a cell through `getVisibleRewardIndex` before removal. Cancelled or invalid command input reopens the same draft and page. Successful input appends to that draft. Deferred rendering checks the current session, permission, connection and inventory before reopening. Opening a view or starting command input advances its revision; delayed close cleanup compares the captured revision so it cannot remove a later phase of the same editor, including accepted input awaiting its delayed reopen.

Chat listeners capture an input token/state before scheduling main-thread work. The manager compare-removes only that exact state, then validates permission. Old queued chat cannot modify a later input session. Pending chat remains cancelled/private. Disconnect clears reward, season and mission input state; callbacks cannot recreate it.

Mission input captures the whole pool's signature, refuses deleted/changed pools and retains input on invalid values or failed writes. Numeric values remain positive except weight, which permits zero. Strict reads and staged saves precede config reload and success messages. Random short IDs retain their format; exhausted ranges use an available extended suffix instead of retrying forever.

See `CODE_AUDIT.md` for regression evidence and `MENUS.md` for general inventory ownership. No live Minecraft server was used for this batch.
