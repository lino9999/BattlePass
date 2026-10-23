# Season reward files and recovery

Read this guide for missing/mixed season rewards, rotation failures, startup archive creation or `season_rotation.yml`. Paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Selection, reload, preflight and public rotation API | `managers/SeasonRotationManager` |
| Recoverable live reward/index replacement | `managers/SeasonFileTransaction` |
| One-file staged/flush/replace operation | `utils/AtomicFile`; YAML wrapper `AtomicYamlFile` |
| Player-reset orchestration and failure/disable | `managers/MissionManager`, `MissionResetHandler` |

Each archived season requires both `BattlePassFREE.yml` and `BattlePassPREMIUM.yml`. Read complete byte snapshots and validate their YAML before changing live files; copy the validated bytes so reward comments/formatting survive. Invalid YAML, missing files, directories at file destinations and changed rotation state propagate errors. Do not restore log-and-continue copy failures or silently combine a new free file with an old premium file.

Rotation/reload publish `currentSeason`, `totalSeasons` and `rotationEnabled` only after persistence succeeds. Rotation updates both reward files and the index together through `SeasonFileTransaction`; public `applySeasonRewards` retains its existing contract of applying files without selecting a new index. Disabled rotation leaves reward files alone. Normalize nonpositive season counts to one and out-of-range integer selections to season one. Invalid state value types are errors; a state file disappearing during runtime is not a fresh installation. Reload explicitly before rotating after an external index change.

`season_rotation.pending.yml` is a runtime recovery record, never a repository artifact. It contains version/status, whitelisted logical filenames, resolved destination identities, existence flags and Base64 backups. Before changing any destination, persist/flush its previous bytes in a prepared record. Replace the files, then persist the committed marker before publishing the selection. On failure, restore old bytes (or the old absence); retain the record if recovery cannot finish. Cleanup failure after a committed marker does not turn a successful rotation into a reported failure.

Every reload and rotation preflight recovers first. Prepared records roll back interrupted replacements; committed records retain the new files and are cleaned up. Validate the entire record, filenames, backups and destination identities before restoring anything. Malformed/changed recovery data must block further writes and remain available for investigation. Do not delete a prepared record to bypass a recovery error. Destination symlinks into season archives are rejected so live writes cannot overwrite an archived source; creating symlinks could not be exercised on the current Windows host.

New default archives stage both files in a temporary sibling directory before publishing the folder. Existing archives remain untouched. A new installation, or first enabling rotation on a server without an archive collection, seeds the selected archive from the live reward pair. `createDefaultSeasonFolders` creates the remaining configured defaults on startup. Its wide loop avoids integer wrap, but automatic creation for an enormous configured count remains a scalability review item.

`MissionManager` validates the next archived pair, current index and live destinations before flushing player data/submitting the season reset; apply validates again. This prevents known missing/corrupt-file failures from erasing player progress. A filesystem failure can still occur after preflight. This file recovery protocol is local to one plugin instance and is not a transaction with the SQL reset/calendar/missions, a distributed lock, or a power-loss/storage durability guarantee. Full season coordination and crash recovery across SQL and files remain under review.

See `CODE_AUDIT.md` for validation evidence and `ARCHITECTURE.md` for daily/season calendar persistence.
