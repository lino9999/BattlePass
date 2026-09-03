# ⚔️ BattlePass - Ultimate Progression Plugin for Minecraft (1.21+)

> **The most advanced, feature-rich Battle Pass system for Spigot & Paper servers.**
> Engage your players with Daily Missions, Tiered Rewards, and a custom Currency Shop.
> **No config editing required** — manage everything via In-Game GUI!

![Java](https://img.shields.io/badge/Java-21-orange) ![Spigot](https://img.shields.io/badge/API-1.21-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

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
Unlike other plugins, **BattlePass** focuses on ease of use for admins and engagement for players. It includes a powerful **In-Game Editor**, robust **MySQL Database** support for networks, and deep integrations with popular plugins like **MythicMobs**.

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

* **💾 Database & Sync Support**
    * **SQLite** (Default): Plug and play for single servers.
    * **MySQL**: Full support for syncing player progress, XP, and rewards across a BungeeCord/Velocity network.

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

1.  Download `BattlePass.jar`.
2.  Drop it into your server's `/plugins/` folder.
3.  (Optional) Install **PlaceholderAPI** for placeholders.
4.  Restart your server.
5.  Enjoy! Config files (`config.yml`, `missions.yml`, `shop.yml`) will generate automatically.

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

*(See `Placeholders.md` for the full list)*

---

## 📸 Screenshots

<img width="927" height="352" alt="image" src="https://github.com/user-attachments/assets/1ed73a90-6776-4746-a52a-7c57d4389cf9" />


---

<div align="center">
  <p>Made with ❤️ by Lino</p>
  <p>Found a bug? Report it in the Issues tab!</p>
</div>
