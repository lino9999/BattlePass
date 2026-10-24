# Custom item effects and sound lifecycle

Read this for voucher/token/boost feedback, repeated held-item sounds, inventory checks and disconnect cleanup. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Item identity and persistent tags | `managers/CustomItemManager` |
| Event registration, hand eligibility, cooldown and quit/shutdown entry points | `listeners/CustomItemsListener` |
| Redemption, stack consumption, XP hook and immediate feedback | `listeners/CustomItemRedemptions` |
| Boost hand, selected slot, item metadata and quantity ownership | `listeners/HeldItemSnapshot` |
| Delayed feedback, coalesced inventory checks and session cleanup | `listeners/CustomItemEffects` |
| Repeating held-item sounds and task ownership | `managers/SoundManager` |
| Shutdown ordering | `BattlePass.onDisable`, `listeners/EventManager.stopCustomItemEffects` |

Item recognition uses persistent tags. The listener dispatches synchronously to its package-private `CustomItemRedemptions`, sharing the same plugin and `CustomItemEffects` instance. The helper owns redemption and is not another registered listener. `CustomItemEffects` only schedules presentation. Premium consumes one item, coins retain uncredited tokens at the wallet limit, and boosts retain the existing XP-event/whole-stack contract. XP-event vouchers consume one item only after event preparation succeeds; rejected/failed starts retain the stack and do not announce activation. Do not move rewards into delayed callbacks. See `EXPERIENCE.md` for multiplier-event ownership and shutdown.

The listener now guards the whole synchronous use by UUID in addition to the existing 50 ms debounce. Nested uses remain blocked even if the hook outlasts that debounce; finally releases the guard after failures. Quit invalidates the current use before effect cleanup, and shutdown terminally closes redemption as well as effects. A retained listener cannot redeem after re-enable.

Before a boost gain event, capture cached PlayerData/revision and a cloned HeldItemSnapshot. Afterwards require the same active use, live player/plugin, cached data/revision, selected main slot (for main hand), item similarity/metadata and exact positive quantity. Off-hand requests also recheck an empty main hand. Recheck max-level eligibility after the hook. Replaced/changed items or reset/quit/stop retain the currently held stack and do not award the old request. Ordinary valid uses and modified amounts are preserved; cancellation still consumes the original stack with zero XP. Progression marks dirty in finally if the original data remains cached, including presentation failures.

The helper is server-thread state. A session binds UUID and the actual Player object, owns its queued effects and one pending inventory check, and is removed before its tasks are cancelled. The five inventory event kinds share one next-tick check per session; the check reads the final held item. Removing the pending check before calling SoundManager allows a later event to schedule another check. Feedback preserves the original configured-by-code counts, delays, sounds, volumes and pitches.

Callbacks must still own their effect/session and require an online player and active plugin. Quit clears the 50 ms custom-use cooldown and all pending effects. A new Player object for the same UUID replaces the previous session. Shutdown closes the helper, removes all sessions and cancels tasks before global repeating sounds stop. Old queued callbacks cannot recreate effects, even if the UUID/player object is reused. Completed effects are removed before presentation runs; scheduling failures also remove their pending ownership so checks can retry.

SoundManager keeps one `ItemSound` object per UUID, with a captured Player and task handle. A callback may render/retire only its currently owned object. Replacement and explicit stops invalidate ownership before cancellation; an old offline callback cannot cancel the replacement. Invalid/offline/disabled/config-disabled players cannot start timers, and running timers recheck availability before reading inventory. An offline old Player check cannot retire another Player object's sound. Playback failures retire ownership rather than leave a phantom active entry.

The repeating period remains two server ticks. Custom item changes retain the current timer phase; ordinary items stop it. Premium, coin and level-boost sound formulas remain in `ItemSound.render`. Event items retain their existing activation feedback rather than gaining an ambient loop. `stopAllSounds` cancels the current collection but remains reusable. `BattlePass.onDisable` instead calls terminal `SoundManager.shutdown`, so a retained old manager cannot recreate sounds after re-enable. Delayed custom-item effects and connection-listener join feedback are also closed separately before final play-time flush.

Automated checks compare all three ambient sequences over 210 callbacks and an item change against their original timing/volume/pitch rules. Scheduling, player state and the API sound registry are mocked; no actual Minecraft sound delivery is established. See `CODE_AUDIT.md` for the full regression evidence.
