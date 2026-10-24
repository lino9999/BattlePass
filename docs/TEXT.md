# Colors, formatting and locale-independent identifiers

Read this for gradient endpoints, Unicode text, legacy styles, GUI materials, target labels and command completion. Java paths are relative to `src/main/java/com/Lino/battlePass/`.

| Responsibility | Entry point |
| --- | --- |
| Public parse/strip API, gradient tags and inline hex codes | `utils/GradientColorParser` |
| Grapheme/control tokenization, color interpolation and style carry | `utils/GradientText` |
| GUI material identifiers and labels | `managers/ConfigManager`, `gui/BaseGui` |
| Newly generated mission target labels | `managers/MissionGenerator.formatTarget` |
| Command routing/item types/tab prefixes | `commands/BattlePassCommand`, `RewardAdminCommands`, `BattlePassTabCompleter` |

ColorParser retains `<gradient:#RRGGBB:#RRGGBB>text</gradient>`, `&#RRGGBB` and legacy `&`/section-sign control codes. Gradient tags may span lines; malformed tags remain literal. This is not a full nested MiniMessage parser. Public null handling and ordinary color/hex conversion remain intact. `stripColors` removes valid gradient markup and both legacy marker forms, including complete RGB control sequences; invalid ampersand codes remain text.

GradientText tokenizes recognized controls before Unicode extended grapheme clusters (Java regex `\X`). Controls and whitespace consume no interpolation steps. Each non-whitespace grapheme receives one RGB prefix, so surrogate pairs, combining accents, variation selectors and joined emoji are not split by inserted color bytes. Two or more visible graphemes reach both endpoint colors; a single grapheme retains the original midpoint rule. Ordinary plain-text interpolation retains the original float/truncation math.

Inserted Minecraft RGB codes reset legacy styles. Carry the five active style flags (`k` through `o`) and reapply them after each RGB prefix. Legacy colors/reset clear those flags; styles before a gradient and across successive tags remain effective until reset. Prefix formatting is scanned once between matches. Controls are preserved for final conversion, preventing inline hex digits from becoming visible gradient text or causing an out-of-range color calculation. Whitespace and visible message content are preserved. These tests verify encoded text, not client-font support for every glyph.

Machine identifiers use Locale.ROOT: materials, command names, item types, tab prefixes, material display labels and generated target labels. Keep original mission names/targets and existing progress keys unchanged when loading persisted missions; this only stabilizes new label generation. Legacy villager-profession fallback code is a separate compatibility path and the unrelated local InteractionMissionTracker edit is not part of this change.

Tab completion retains permissions and suggestion ordering, filters online player names through the same case-independent typed-prefix filter as other suggestions, and returns an empty list for zero arguments. Reward factories/delivery are unchanged by case normalization. See `CODE_AUDIT.md` for verification and `DEVELOPMENT.md` for the temporary-test policy.
