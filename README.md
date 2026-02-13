# ⚔️ BattlePass - Ultimate Progression Plugin for Minecraft (1.21+)

> **The most advanced, feature-rich Battle Pass system for Spigot & Paper servers.**
> Engage your players with Daily Missions, Tiered Rewards, and a custom Currency Shop.
> **No config editing required** — manage everything via In-Game GUI!

![Java](https://img.shields.io/badge/Java-21-orange) ![Spigot](https://img.shields.io/badge/API-1.21-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

---

## 🌟 Why Choose BattlePass?
Unlike other plugins, **BattlePass** focuses on ease of use for admins and engagement for players. It includes a powerful **In-Game Editor**, robust **MySQL Database** support for networks, and deep integrations with popular plugins like **MythicMobs**.

### 🔥 Key Features

* **🏆 Seasonal Progression System**
    * Fully customizable tier system (default 54 levels).
    * **Dual Reward Tracks**: Free Pass (for everyone) and Premium Pass (VIP/Paid).
    * Automatic season reset options (Monthly or Duration-based).

* **🛠️ In-Game GUI Editor (No YAML needed!)**
    * **Mission Editor**: Create, edit, or delete daily missions directly inside the game.
    * **Rewards Editor**: Drag-and-drop items from your inventory to set rewards for any level.

* **💾 Database & Sync Support**
    * **SQLite** (Default): Plug and play for single servers.
    * **MySQL**: Full support for syncing player progress, XP, and rewards across a BungeeCord/Velocity network.

* **📜 Dynamic Missions**
    * **7 Daily Missions** generated randomly every day.
    * **Mission Types**: Mining, Crafting, Fishing, Farming, Killing Mobs, Playtime, Walking Distance, and more!.

* **💰 Battle Coins & Shop**
    * Players earn **Battle Coins** by ranking in the daily leaderboard.
    * Spend coins in the customizable **Shop GUI** for exclusive items, XP boosts, or commands.

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
| `/bp giveitem <type> <player> <amount>` | `battlepass.admin` | Give special items (Premium Voucher, Coins, XP Boosts). |
| `/bp addpremium <player>` | `battlepass.admin` | Force unlock Premium Pass for a player. |
| `/bp addxp <player> <amount>` | `battlepass.admin` | Give XP to a player. |
| `/bp reset season` | `battlepass.admin` | Force reset the entire season progress. |
| `/bp reset missions` | `battlepass.admin` | Force generate new daily missions. |
| `/bp resetplayer <name>` | `battlepass.admin` | Reset battle pass progress for a player. |
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

*(See `Placeholders.md` for the full list)*

---

## 📸 Screenshots

<img width="927" height="352" alt="image" src="https://github.com/user-attachments/assets/1ed73a90-6776-4746-a52a-7c57d4389cf9" />


---

<div align="center">
   <p>I've just launched https://www.hytaleservers.it/</p>
   <p>Are you working on a server? List it now for free and build your audience before launch.​</p>
</div>

---

<div align="center">
  <p>Made with ❤️ by Lino</p>
  <p>Found a bug? Report it in the Issues tab!</p>
</div>
