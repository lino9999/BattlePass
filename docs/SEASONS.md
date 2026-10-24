# Season reward files and recovery

Read this guide for missing/mixed season rewards, rotation failures, startup archive creation or `season_rotation.yml`. Paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Selection, reload, preflight and public rotation API | `managers/SeasonRotationManager` |
| Recoverable live reward/index replacement | `managers/SeasonFileTransaction` |
| One-file staged/flush/replace operation | `utils/AtomicFile`; YAML wrapper `AtomicYamlFile` |
| Player-reset orchestration and failure/disable | `managers/MissionManager`, `MissionResetHandler` |
| SQL season snapshot, durable intent and acknowledgement | `managers/SeasonResetPlan`, `SeasonTransitionRepository`, `DatabaseManager` |

Each archived season requires both `BattlePassFREE.yml` and `BattlePassPREMIUM.yml`. Read complete byte snapshots and validate their YAML before changing live files; copy the validated bytes so reward comments/formatting survive. Invalid YAML, missing files, directories at file destinations and changed rotation state propagate errors. Do not restore log-and-continue copy failures or silently combine a new free file with an old premium file.

Rotation/reload publish `currentSeason`, `totalSeasons` and `rotationEnabled` only after persistence succeeds. Rotation updates both reward files and the index together through `SeasonFileTransaction`; public `applySeasonRewards` retains its existing contract of applying files without selecting a new index. Disabled rotation leaves reward files alone. Normalize nonpositive season counts to one and out-of-range integer selections to season one. Invalid state value types are errors; a state file disappearing during runtime is not a fresh installation. Reload explicitly before rotating after an external index change.

`season_rotation.pending.yml` is a runtime recovery record, never a repository artifact. It contains version/status, whitelisted logical filenames, resolved destination identities, existence flags and Base64 backups. Before changing any destination, persist/flush its previous bytes in a prepared record. Replace the files, then persist the committed marker before publishing the selection. On failure, restore old bytes (or the old absence); retain the record if recovery cannot finish. Cleanup failure after a committed marker does not turn a successful rotation into a reported failure.

Every reload and rotation preflight recovers first. Prepared records roll back interrupted replacements; committed records retain the new files and are cleaned up. Validate the entire record, filenames, backups and destination identities before restoring anything. Malformed/changed recovery data must block further writes and remain available for investigation. Do not delete a prepared record to bypass a recovery error. Destination symlinks into season archives are rejected so live writes cannot overwrite an archived source; creating symlinks could not be exercised on the current Windows host.

New default archives stage both files in a temporary sibling directory before publishing the folder. Existing archives remain untouched. A new installation, or first enabling rotation on a server without an archive collection, seeds the selected archive from the live reward pair. `createDefaultSeasonFolders` creates the remaining configured defaults on startup. Its wide loop avoids integer wrap, but automatic creation for an enormous configured count remains a scalability review item.

`MissionManager` validates the next archived pair, current index and live destinations before flushing player data/submitting the season reset; apply validates again. It captures a `SeasonResetPlan` with a unique ID, explicit target season (zero for no rotation), coin-reset setting, calendar, date and copied mission values. `MissionResetHandler` stages that plan immediately before calling the stable `DatabaseManager.resetSeason()` entry point. The internal staging slot belongs to the submitting thread and is removed before queueing; do not stage it inside a database job or leave callbacks between preparation/submission.

`SeasonTransitionRepository` uses one connection/transaction for player reset, progress deletion, replacement missions, calendar/mission anchor and the durable intent (`season_data.season_transition_id`, `pending_rotation_season`). SQL failure rolls everything back. Another pending intent blocks subsequent composite and legacy low-level player resets. Existing calendar/daily save entry points remain ordered, idempotent refreshes; they do not erase intent columns.

After SQL commits, apply the explicit target through the file recovery protocol, reload rewards and acknowledge exactly that transition ID in SQL. Only then publish the new mission list/date, resume progress and notify/reload players. A failed apply/acknowledgement pauses progress and disables the plugin while leaving a recoverable intent. Startup finishes that intent before checking another season expiry or loading missions. Reapplying the same target is idempotent, so a crash after file success and before acknowledgement cannot advance another season or reset player data again. Recovery honors a committed target even if automatic rotation was disabled later; missing archives, incompatible configured ranges or incomplete calendars require restoring the data/configuration rather than inventing another season.

Final acknowledgement waits for a short database operation on the server thread; never hold the player-cache monitor while joining it. Shutdown can finalize a committed transition directly, and its previously queued finalizer remains idempotent. This is a recoverable sequence across SQL and files, not a single cross-system transaction, a distributed lock or a power-loss/storage durability guarantee. MySQL requires transactional tables. Standalone legacy reset-handler APIs, command completion reporting, time-accounting boundaries and shared-server cache behavior remain separate review items.

See `CODE_AUDIT.md` for validation evidence and `ARCHITECTURE.md` for daily/season calendar persistence.
