⚔️ BattlePass
Bring the excitement of a seasonal progression system to your Minecraft server!

BattlePass is a feature-rich plugin that adds a tier-based reward system similar to popular battle royale games. Engage your players with Daily Missions, unlockable rewards, and a currency system, all manageable through an easy-to-use in-game GUI.

(Replace this with your own image)

✨ Key Features
🏆 Seasonal Progression: Players can level up through 54 tiers (configurable) to unlock rewards.

🔓 Dual Reward Tracks: Support for both Free (for everyone) and Premium (VIP) reward tracks.

📜 Daily Missions: 7 random missions generated daily (e.g., Mining, Mob Killing, Fishing) to earn XP.

🛠️ In-Game Editor: No config editing required! Modify rewards and create missions directly inside the game using a GUI.

💰 Battle Coins Shop: Players earn coins to spend in a custom shop for exclusive items or boosts.

📊 Leaderboards: Competitive top-10 ranking based on Battle Pass levels.

💾 Database Support: Built-in support for SQLite (default) and MySQL for network syncing.

🔌 Integrations:

PlaceholderAPI: Full placeholder support for scoreboards and menus.

MythicMobs: Create missions to kill custom mobs.

📥 Installation
Download the .jar file.

Place it in your server's plugins folder.

(Optional) Install PlaceholderAPI for placeholder support.

Restart your server.

Done! The configuration files will generate automatically.

🎮 Commands
Player Commands
/bp (or /battlepass) - Opens the main Battle Pass menu.

/bp help - Shows the help menu.

Admin Commands
/bp giveitem <type> <player> <amount> - Give special items:

premium - Voucher to unlock the Premium pass.

coins - Currency item.

levelboost - Item that grants XP when used.

/bp addxp <player> <amount> - Give XP to a player.

/bp addpremium <player> - Force unlock Premium for a player.

/bp reset season - Force reset the entire season (Warning: Clears progress!).

/bp reset missions - Force generate new daily missions.

/bp reload - Reload configuration files.


⚙️ Configuration
The plugin is highly configurable. You can find these files in the /plugins/BattlePass/ folder:

config.yml: General settings (Season duration, database type, XP curve).

missions.yml: Configure the types of missions available (or use the in-game editor!).

shop.yml: Edit items available in the Battle Coin shop.

messages.yml: Translate the plugin into your language.

🧩 Placeholders
Fully compatible with PlaceholderAPI. Here are a few useful ones:

%battlepass_level% - Player's current level.

%battlepass_xp_progress% - Formatted XP progress (e.g., 50/200).

%battlepass_premium_status% - Returns "Active" or "Inactive".

%battlepass_season_time% - Time remaining in the season.


<div align="center"> <p>Made with ❤️ by Lino</p> </div>
