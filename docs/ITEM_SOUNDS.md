# Custom item effects and sound lifecycle

Read this for voucher/token/boost feedback, repeated held-item sounds, inventory checks and disconnect cleanup. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Item identity and persistent tags | `managers/CustomItemManager` |
| Redemption, hand/stack rules, cooldown and event registration | `listeners/CustomItemsListener` |
| Delayed feedback, coalesced inventory checks and session cleanup | `listeners/CustomItemEffects` |
| Repeating held-item sounds and task ownership | `managers/SoundManager` |
| Shutdown ordering | `BattlePass.onDisable`, `listeners/EventManager.stopCustomItemEffects` |

Item recognition uses persistent tags. Redemption and player-state changes stay in the existing listener; `CustomItemEffects` only schedules presentation. Premium consumes one item, coins retain uncredited tokens at the wallet limit, and boosts retain the existing XP-event/whole-stack contract. XP-event vouchers consume one item only after event preparation succeeds; rejected/failed starts retain the stack and do not announce activation. Do not move rewards into delayed callbacks. See `EXPERIENCE.md` for multiplier-event ownership and shutdown.

The helper is server-thread state. A session binds UUID and the actual Player object, owns its queued effects and one pending inventory check, and is removed before its tasks are cancelled. The five inventory event kinds share one next-tick check per session; the check reads the final held item. Removing the pending check before calling SoundManager allows a later event to schedule another check. Feedback preserves the original configured-by-code counts, delays, sounds, volumes and pitches.

Callbacks must still own their effect/session and require an online player and active plugin. Quit clears the 50 ms custom-use cooldown and all pending effects. A new Player object for the same UUID replaces the previous session. Shutdown closes the helper, removes all sessions and cancels tasks before global repeating sounds stop. Old queued callbacks cannot recreate effects, even if the UUID/player object is reused. Completed effects are removed before presentation runs; scheduling failures also remove their pending ownership so checks can retry.

SoundManager keeps one `ItemSound` object per UUID, with a captured Player and task handle. A callback may render/retire only its currently owned object. Replacement and explicit stops invalidate ownership before cancellation; an old offline callback cannot cancel the replacement. Invalid/offline/disabled/config-disabled players cannot start timers, and running timers recheck availability before reading inventory. An offline old Player check cannot retire another Player object's sound. Playback failures retire ownership rather than leave a phantom active entry.

The repeating period remains two server ticks. Custom item changes retain the current timer phase; ordinary items stop it. Premium, coin and level-boost sound formulas remain in `ItemSound.render`. Event items retain their existing activation feedback rather than gaining an ambient loop. `stopAllSounds` cancels the current collection but remains reusable. `BattlePass.onDisable` instead calls terminal `SoundManager.shutdown`, so a retained old manager cannot recreate sounds after re-enable. Delayed custom-item effects and connection-listener join feedback are also closed separately before final play-time flush.

Automated checks compare all three ambient sequences over 210 callbacks and an item change against their original timing/volume/pitch rules. Scheduling, player state and the API sound registry are mocked; no actual Minecraft sound delivery is established. See `CODE_AUDIT.md` for the full regression evidence.
