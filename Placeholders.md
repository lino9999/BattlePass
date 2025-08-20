# BattlePass PlaceholderAPI Placeholders

## Installation
Make sure you have PlaceholderAPI installed on your server. The BattlePass plugin will automatically detect and register placeholders when PlaceholderAPI is present.

## Available Placeholders

### Player Statistics
- `%battlepass_level%` - Current battle pass level
- `%battlepass_xp%` - Current XP amount
- `%battlepass_xp_needed%` - XP needed for next level
- `%battlepass_xp_progress%` - Shows XP progress (current/needed)
- `%battlepass_xp_percentage%` - XP progress as percentage
- `%battlepass_total_levels%` - Total levels earned all-time

### Premium Status
- `%battlepass_premium%` - Shows "Yes" or "No"
- `%battlepass_premium_status%` - Shows "Active" or "Inactive"

### Currency
- `%battlepass_coins%` - Current battle coins
- `%battlepass_battlecoins%` - Same as above

### Rewards
- `%battlepass_available_rewards%` - Number of unclaimed rewards available

### Time Remaining
- `%battlepass_season_time%` - Time until season ends
- `%battlepass_season_remaining%` - Same as above
- `%battlepass_missions_time%` - Time until missions reset
- `%battlepass_missions_reset%` - Same as above
- `%battlepass_daily_reward_time%` - Time until daily reward available
- `%battlepass_daily_reward_available%` - Shows "Yes" or "No"
- `%battlepass_coins_distribution_time%` - Time until next coins distribution

### Leaderboard
- `%battlepass_rank%` - Player's rank on leaderboard
- `%battlepass_leaderboard_rank%` - Same as above

### Missions
- `%battlepass_completed_missions%` - Number of completed daily missions
- `%battlepass_total_missions%` - Total number of daily missions
- `%battlepass_missions_progress%` - Shows mission progress (completed/total)

### Individual Mission Placeholders
For each mission slot (1-7):
- `%battlepass_mission_progress_X%` - Progress of mission X (e.g., "5/10")
- `%battlepass_mission_name_X%` - Name of mission X
- `%battlepass_mission_status_X%` - Status of mission X ("Completed" or "In Progress")

Replace X with the mission number (1-7)

### Top Players
For top 10 players on leaderboard:
- `%battlepass_top_X_name%` - Name of player at position X
- `%battlepass_top_X_level%` - Level of player at position X
- `%battlepass_top_X_xp%` - XP of player at position X
- `%battlepass_top_X_coins%` - Coins of player at position X
- `%battlepass_top_X_totallevels%` - Total levels of player at position X

Replace X with the position (1-10)

## Examples

### Scoreboard Example
```
&6Battle Pass
&7Level: &e%battlepass_level%
&7XP: &e%battlepass_xp_progress%
&7Premium: &e%battlepass_premium_status%
&7Coins: &e%battlepass_coins%
```

### Hologram Example
```
&6&lTOP PLAYERS
&e#1 &f%battlepass_top_1_name% &7- Lvl &e%battlepass_top_1_level%
&e#2 &f%battlepass_top_2_name% &7- Lvl &e%battlepass_top_2_level%
&e#3 &f%battlepass_top_3_name% &7- Lvl &e%battlepass_top_3_level%
```

### Tab List Example
```
&7[&eLvl %battlepass_level%&7] &f%player_name%
```
