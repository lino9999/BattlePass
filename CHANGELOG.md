# Changelog

## 8.6

### Database upgrades and version reporting (#20, #21)
- The plugin version now comes from the Maven project version, so the JAR name and `plugin.yml` cannot disagree. The update checker compares numeric releases and no longer advertises an older published version as an update.
- Fixed MySQL upgrades failing to add `daily_missions.additional_targets`: the migration no longer uses the unsupported `TEXT DEFAULT ''` definition.
- Existing SQLite and MySQL databases now receive missing season start and coin distribution columns automatically. Migrations check for existing columns and tolerate another server adding the same column concurrently.
- Database initialization and migration errors now stop plugin startup with a diagnostic instead of reporting success and repeatedly failing later.

### Follow-up fixes for previously reported issues (#1, #2, #19)
- XP gain events now implement Bukkit's `Cancellable` interface, allowing external listeners using `ignoreCancelled` to behave correctly.
- Closing another plugin's inventory no longer affects BattlePass's reward editor state, even if its title matches a BattlePass editor.
- Restarting the coin distribution task after a manual season reset preserves the current deadline. Changes to the managed task are performed on the server thread.

### Season duration - fixed
- Fixed automatic and manual resets saving the previous season's deadline before the new one was calculated. Restarting could previously end the new season early or reset it again immediately.
- The season start date is now persisted. Changes to `season.duration` apply to the current season on `/bp reload` or restart, counting from that start date instead of restarting the countdown each time. If the shortened duration has already elapsed, the season resets on the next check.
- Season saves are ordered, including on MySQL, so an older save cannot overwrite a newer deadline. New daily missions are saved after the season reset has finished deleting the old missions.
- Upgrade behavior: old databases have no season start date. On the first startup after upgrading, an existing `DURATION` season receives a fresh countdown using the configured duration, preserving player progress, premium access and rewards. This one-time migration is logged. `MONTH_START` seasons retain their calendar deadline.

## Previous updates

### Battle Coins delivery - fixed
- Fixed the problem where Battle Coins were never delivered at the scheduled time. The delivery system now always starts correctly and keeps its schedule.
- The delivery countdown is now saved in the database, so it survives server restarts, plugin reloads and season resets instead of resetting every time.
- Fixed a problem where a manual season reset could create a second hidden delivery task, which would have paid the top players twice. There is now a single managed task.
- The "top players are receiving coins" announcement no longer appears when there is nobody eligible to receive them.
- Online players' coin balances are now updated correctly during delivery instead of being overwritten with old saved values.
- Player names in the delivery announcement no longer show as "null" in rare cases, and the countdown display can no longer show negative times.

### Translations
- The "Current Season" line in the progress item is now translatable in messages.yml (items.progress.current-season) - it was hardcoded in English before.
- All remaining chat messages are now configurable in messages.yml, including the leaderboard exclude/include command output, the mission-not-found message and the actionbar fallback text. Existing servers keep the English texts automatically until they edit them.

### New options added (from community requests)
- Missions can now be disabled per world with the new missions.disabled-worlds list in config.yml.
- The mission actionbar durations are now configurable (missions.actionbar.progress-duration and completed-duration, in seconds).
- GUI items now support custom model data for resource packs: every reward item in config.yml can use a material + custom-model-data section, and the page arrows support gui.navigation.custom-model-data.
- New developer API: the plugin now fires a BattlePassXPGainEvent whenever a player earns Battle Pass XP (from missions, the daily reward or XP elixirs). Other plugins can listen to it and change or cancel the amount - useful for XP multiplier plugins.
- The project now includes an MIT license, so everyone can use, modify and share the plugin freely.

### Missions
- Missions now support additional targets: for example "mine iron ore" can also count deepslate iron ore, "kill zombies" can also count husks and drowned, and so on for many mission types.
- Expanded mission types: damage missions can target specific creatures or damage causes (fall, fire, lava...), trade missions can target specific villager professions, enchant missions can target specific items, movement missions can require walking, swimming, flying or sneaking.
- Death and damage-dealt missions no longer count twice for a single event.
- A broken or empty missions.yml can no longer freeze the whole plugin: the plugin starts normally and shows no missions instead.
- The mission editor no longer crashes on a mission without a type, rejects invalid values (zero or negative), and can no longer overwrite an existing mission by generating a duplicate name.
- daily-missions-count is now capped at 7 (the missions menu can only display 7 missions - extra missions used to be invisible but still active).
- Fixed a mission name typo and corrected the documentation for valid movement targets (SNEAK) and villager professions.

### Custom items
- Premium vouchers, coin tokens, XP elixirs and event beacons can now be used from the off-hand as well as the main hand, and always consume the correct hand.
- One right-click can no longer consume two items at once.

### Economy, shop and rewards
- Coins and shop purchases made right before a season reset are no longer lost: pending data is saved before the reset.
- A broken command inside a shop item or a level reward no longer swallows the purchase/reward: everything else is still delivered and the error is logged.
- Purchased shop items no longer disappear when the player's inventory is full - they drop on the ground instead.
- An invalid item in shop.yml no longer crashes the plugin at startup, and an invalid shop slot no longer breaks the shop menu.
- Reward notifications ("you have new rewards to claim") now work again after a player claims rewards - before they stayed silent until a big batch arrived.

### Safety against invalid configuration values
- Invalid time values can no longer cause reset loops: a season duration, coin delivery interval or mission reset interval of zero used to reset everything every minute. These values are now forced to at least 1.
- The XP needed per level can no longer be zero (which made players jump straight to max level).
- Shop prices can no longer be negative (which let players gain coins by buying).
- Item amounts in rewards can no longer be zero or negative, and the MySQL connection pool size is now validated.
- Negative or zero mission weights no longer break mission generation.

### Menus and general stability
- Fixed a problem where the plugin's click handlers could interfere with GUIs from other plugins (for example other plugins' editors): BattlePass now only reacts to its own menus.
- Opening menus, using items or running admin commands immediately after joining no longer crashes when the player data is still loading - a friendly "data is loading, try again in a moment" message is shown instead.
- The leaderboard and the editors no longer glitch if a player disconnects at the wrong moment.
- Safer first-run database writes (relevant for MySQL networks).
- Season rotations are protected against being triggered twice in quick succession.
- All material, mob and profession names in the default configurations have been verified against Minecraft 1.21.4, and the updated documentation now lists all available placeholders (including season and XP event placeholders).

### Technical notes
- Requires Java 21 and Minecraft 1.21.x (Spigot/Paper).
- SQLite works out of the box; MySQL is fully supported for networks.
- Soft dependencies: PlaceholderAPI, MythicMobs.
