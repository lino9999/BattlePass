# Managed YAML loading and reload failures

Read this for invalid YAML, lost translations/rewards after reload, startup errors or shop configuration. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Strict managed-file reads/preflight | `utils/ConfigurationFiles`, `AtomicYamlFile` |
| Cached settings and four auxiliary YAML snapshots | `managers/ConfigManager.reload` |
| GUI materials/defaults, NONE visibility and custom model data | `managers/GuiConfiguration`, exposed by existing `ConfigManager` getters |
| Custom messages plus in-memory packaged defaults | `managers/MessageManager.reload` |
| Candidate shop map and material validation | `managers/ShopManager.loadShop` |
| Startup/reload ordering and synchronous startup failure | `BattlePass.onEnable`, `reload` |
| Recovering interrupted live reward replacements before validation | `SeasonRotationManager.recoverFilesBeforeStartup`, `recoverPendingFiles` |

YamlConfiguration.loadConfiguration logs errors and can return an empty document. Do not use it where a failed managed-file read would be mistaken for a successful empty reload. ConfigurationFiles wraps strict AtomicYamlFile reads and reports the filename/cause with IllegalStateException. The managed preflight checks `config.yml`, `missions.yml`, `messages.yml`, both live reward files and `shop.yml`.

Startup first saves missing packaged resources, recovers any interrupted season-file journal, then validates the managed files before constructing managers. Recovery must precede YAML validation: a prepared journal may own a temporarily incomplete live reward file. Runtime reload likewise performs locked recovery before preflight and before JavaPlugin.reloadConfig or manager publication. A malformed journal blocks further work and remains available. Synchronous startup/resource/constructor failures invalidate startup work, log the error and explicitly disable the partial plugin; asynchronous startup continues to use its existing generation checks.

ConfigManager reads all four auxiliary documents into local candidates before changing cached settings/material maps/reward references. MessageManager reads custom YAML and strict packaged defaults before publishing its volatile configuration reference; custom values win and defaults never rewrite the user's file. Read/parse failures keep the previous component state and propagate to the reload command's failure reporting. ShopManager parses into a candidate map before replacing its existing map.

GuiConfiguration owns the GUI-only fields and parsing methods formerly in ConfigManager. The facade calls its reload at the same point after auxiliary reads and database settings, before mission settings. Preserve string/section syntax, Locale.ROOT material lookup, warning/default behavior, NONE handling for claimed rewards, positive-only per-icon model data and nonnegative navigation model data. Public ConfigManager methods and mutable YAML compatibility getters remain unchanged; callers do not construct this internal helper.

BattlePassRewardRenderer additionally checks that configured icon materials are usable non-air items at render time, falling back to the existing per-control defaults and retaining claimed NONE hiding. RewardConfiguration/ItemSerializer validate delivered reward items separately; active/archive parsing shares the same definitions and labels. See REWARDS.md.

Existing missing-file/default behavior is retained: startup saves missing resources, ConfigManager can reseed missing free/premium files, and ShopManager can reseed a missing shop file. A deliberately empty valid reward document is still accepted. This is syntax/read validation with the existing field defaults/clamps, not universal schema enforcement. Shop icons normalize case with Locale.ROOT and fall back to STONE for air/non-item/unknown materials; invalid actual reward-item entries are warned/skipped. Other valid shop entries, ordering, reserved slots and prices retain their existing behavior.

The preflight and per-component publication do not form one transaction across all configuration, rotation files and SQL, or guarantee consistency against concurrent external edits between reads. Season-file journaling and SQL-intent recovery remain separate protocols; read `SEASONS.md` before changing them. Runtime-facing FileConfiguration getters remain mutable compatibility APIs.

See `CODE_AUDIT.md` for malformed-file/directory/default-stream, recovery-order and startup verification. Tests use real temporary YAML/journals and mocked Bukkit item/block registries and lifecycle; no live Minecraft server was used.
