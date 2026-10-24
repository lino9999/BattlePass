# ⚔️ BattlePass - Minecraft Battle Pass Plugin for Paper & Spigot (1.21.11–26.3)

BattlePass adds daily quests, seasonal progression, Free and Premium reward tracks, and Battle Coins to your Minecraft Java server. Create missions and rewards through in-game editors, with optional PlaceholderAPI and MythicMobs support.

**[Download the latest JAR](https://github.com/lino9999/BattlePass/releases/latest)** · [What's new](docs/RELEASE_NOTES.md) · [Report a bug](https://github.com/lino9999/BattlePass/issues)

![Java](https://img.shields.io/badge/Java-21%20%2F%2025-orange) ![Spigot](https://img.shields.io/badge/Minecraft-1.21.11%E2%80%9326.3-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

---

<div align="center">

### Support the Project

BattlePass is free and open source, and it is developed and maintained in my spare time.
If this plugin saves you time or helps your server grow, consider buying me a coffee.
Every donation keeps the updates coming and helps me invest even more time into new features.

[![Buy Me A Coffee](https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&slug=lino9999&button_colour=FFDD00&font_colour=000000&font_family=Poppins&outline_colour=000000&coffee_colour=ffffff)](https://buymeacoffee.com/lino9999)

</div>

---

## 🌟 Why Choose BattlePass?
**BattlePass** gives server admins in-game editors for missions and rewards, SQLite or MySQL storage, and optional integrations with plugins such as **MythicMobs**.

### 🔥 Key Features

* **🏆 Seasonal Progression System**
    * Fully customizable tier system (default 54 levels).
    * **Dual Reward Tracks**: Free Pass (for everyone) and Premium Pass (VIP/Paid).
    * Automatic season reset options (Monthly or Duration-based).
    * **Season Rotation**: automatically cycle through multiple reward sets stored in the `seasons/` folder, each season with its own Free and Premium rewards.

* **🛠️ In-Game GUI Editor (No YAML needed!)**
    * **Mission Editor**: Create, edit, or delete daily missions directly inside the game.
    * **Rewards Editor**: Drag-and-drop items from your inventory to set rewards for any level.
    * **Season Editing**: Edit the rewards of any season in the rotation, even while another season is active.

* **💾 Database Storage**
    * **SQLite** (Default): Plug and play for single servers.
    * **MySQL**: Store player progress, XP, coins and reward claims in a MySQL database. Live synchronization between servers is not provided.

* **📜 Dynamic Missions**
    * **7 Daily Missions** generated randomly every day (count configurable).
    * **Mission Types**: Mining, Crafting, Fishing, Farming, Killing Mobs, Playtime, Walking Distance, Dealing/Taking Damage, Trading with Villagers, Enchanting, and more.
    * **Additional Targets**: one mission can accept multiple materials, mobs, or professions (e.g. "mine iron" also counts deepslate iron ore).
    * **World Blacklist**: Disable mission progress in selected worlds (`missions.disabled-worlds`).
    * **Configurable actionbar**: Set how long mission progress messages stay visible.

* **💰 Battle Coins & Shop**
    * Players earn **Battle Coins** by ranking in the daily leaderboard.
    * Spend coins in the customizable **Shop GUI** for exclusive items, XP boosts, or commands.
    * Top 10 payouts, delivery interval, and rewards are fully configurable.

* **Custom Items & XP Events**
    * **Premium Voucher**, **Battle Coin Token**, **Experience Elixir** and **XP Event Beacon** items, obtainable via `/bp giveitem` or as rewards.
    * **Server-wide XP events** (`/bp event 2x 1h`) with a live bossbar countdown for all players.

* **Daily Rewards**
    * Players claim a free XP bonus once per day from the Battle Pass menu.

* **Fully Translatable**
    * Every message, menu text, and GUI line is configurable in `messages.yml`, including the season display.

* **Developer API**
    * `BattlePassXPGainEvent`: listen to, modify, or cancel Battle Pass XP gains to integrate your own multipliers or systems.

* **GUI Customization**
    * Change every menu material in `config.yml`, with support for **custom model data** for resource packs.

* **🔌 Powerful Integrations**
    * **PlaceholderAPI**: Full support for scoreboards, tabs, and chat.
    * **MythicMobs**: Create missions to kill specific custom bosses or mobs.

---

## 📥 Installation

Supports Spigot and available Paper releases from Minecraft Java 1.21.11 through 26.3. Use Java 21 on 1.21.11 and Java 25 on 26.x. See the [compatibility matrix](docs/COMPATIBILITY.md) for exact builds and verification limits. Paper has no published 26.1 build; its 26.1.1 build is alpha.

1.  Download `BattlePass-8.6.1.jar` from the latest [GitHub release](https://github.com/lino9999/BattlePass/releases/latest). Choose the JAR under **Assets**, rather than the source-code ZIP.
2.  Drop it into your server's `/plugins/` folder.
3.  (Optional) Install **PlaceholderAPI** for placeholders.
4.  Restart your server.
5.  Enjoy! Config files (`config.yml`, `missions.yml`, `shop.yml`) will generate automatically.

### Updating an existing server

1. Stop the server and back up `plugins/BattlePass/` and its database (including the MySQL database if used).
2. Replace the old BattlePass JAR in `plugins/` with the new one, keeping only one BattlePass JAR installed.
3. Keep your existing configuration and data files, then start the server and check the startup log.

This release includes the [latest and previous fixes](docs/RELEASE_NOTES.md). Golden apple and block missions accept names entered through the editor more reliably. Use `BREAK_BLOCK` for breaking blocks and `PLACE_BLOCK` for placement; `MINE_BLOCK` remains limited to ores. New blocks are available only on server versions that include them. Mission troubleshooting is in [the mission guide](docs/MISSIONS.md).

When reporting a problem, include the Minecraft version, Paper/Spigot build, BattlePass version, relevant mission settings and any error log. Remove passwords and other private information before sharing configuration.

### Build from source

Use Java 21 and Maven, then run `mvn clean package`. The build runs the regression tests and produces `target/BattlePass-8.6.1.jar`. The release version is defined in `pom.xml`; Maven also writes it into the packaged `plugin.yml`.

See [the issue review](docs/ISSUE_REVIEW.md) for the fixes, configuration examples and validation details. Existing databases are migrated automatically at startup. If MySQL reports an `ALTER` permission error, grant the plugin's database account permission to update its tables before restarting.

For contributors and AI agents, start with the [development quick reference](docs/README.md) for feature entry points, mission troubleshooting, tests and the commit/push workflow.

---

## 🎮 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp` or `/battlepass` | `battlepass.use` | Opens the main Battle Pass menu. |
| `/bp help` | `battlepass.use` | Shows the help menu. |
| `/bp giveitem <type> <player> <amount>` | `battlepass.admin` | Give special items (premium, coins, levelboost, xpevent). |
| `/bp addpremium <player>` | `battlepass.admin` | Force unlock Premium Pass for a player. |
| `/bp removepremium <player>` | `battlepass.admin` | Remove the Premium Pass from a player. |
| `/bp addxp <player> <amount>` | `battlepass.admin` | Give XP to a player. |
| `/bp removexp <player> <amount>` | `battlepass.admin` | Remove XP from a player. |
| `/bp addcoins <player> <amount>` | `battlepass.admin` | Give Battle Coins to a player. |
| `/bp removecoins <player> <amount>` | `battlepass.admin` | Remove Battle Coins from a player. |
| `/bp reset season` | `battlepass.admin` | Force reset the entire season progress. |
| `/bp reset missions` | `battlepass.admin` | Force generate new daily missions. |
| `/bp resetplayer <name>` | `battlepass.admin` | Reset battle pass progress for a player. |
| `/bp excludefromtop <player>` | `battlepass.admin` | Hide a player from the leaderboard. |
| `/bp includetop <player>` | `battlepass.admin` | Show a player in the leaderboard again. |
| `/bp event <multiplier> <duration>` | `battlepass.admin` | Start a server-wide XP event (e.g. `/bp event 2x 1h`). |
| `/bp stopevent` | `battlepass.admin` | Stop the active XP event. |
| `/bp edit rewards season <number>` | `battlepass.admin` | Edit the rewards of a specific season (season rotation). |
| `/bp reload` | `battlepass.admin` | Reloads all configuration files. |

---

## 🧩 Placeholders (PAPI)

Add these to your scoreboard or tablist!

* `%battlepass_level%` - Player's current tier.
* `%battlepass_xp_progress%` - Formatted XP (e.g., 50/200).
* `%battlepass_premium_status%` - Returns "Active" or "Inactive".
* `%battlepass_season_time%` - Time remaining in the current season.
* `%battlepass_coins%` - Current Battle Coins balance.
* `%battlepass_daily_reward_available%` - Check if daily reward is ready ("Yes"/"No").
* `%battlepass_current_season%` - Current season number (season rotation).
* `%battlepass_xp_event_multiplier%` - Active XP event multiplier (e.g., "2x").

See [Placeholders.md](Placeholders.md) for the full list.

---

## 📸 Screenshots

<img width="927" height="352" alt="image" src="https://github.com/user-attachments/assets/1ed73a90-6776-4746-a52a-7c57d4389cf9" />


---

<div align="center">
  <p>Made with ❤️ by Lino</p>
  <p>Found a bug? Report it in the Issues tab!</p>
</div>
