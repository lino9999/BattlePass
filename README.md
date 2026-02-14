# BattlePass Fork (Paper 1.16.5 + Java 17)

Fork of BattlePass prepared for legacy Paper servers with cleaner config/gui structure.

## Compatibility

- Java: `17`
- Server API: `Paper 1.16.5` (`paper-api:1.16.5-R0.1-SNAPSHOT`)
- Plugin `api-version`: `1.16`

## What Is Included

- Player GUI cleanup and localization split by language.
- Language switch in `config.yml`: `language: RU` or `language: ENG` (`EN` also works).
- Separate files:
  - `messages_ru.yml` / `messages_en.yml`
  - `gui_ru.yml` / `gui_en.yml`
- HEX color support:
  - `&#RRGGBB`
  - `<#RRGGBB>`
- Cleaner custom items config in GUI language files.
- Material display names localized by language (RU or readable EN).
- Config validation on startup/reload for common material/command mistakes.

## Build

```bash
mvn -DskipTests package
```

Output jar:

- `target/BattlePass-1.0.jar`

## Language Setup

In `config.yml`:

```yml
language: RU
```

Available values:

- `RU`
- `ENG` (or `EN`)

## Notes

- If your data folder already exists, new language files are auto-created only when missing.
- After language/config edits, use `/bp reload`.
