# Working on BattlePass

- Start with `docs/README.md`, then read only the task's relevant guide and files.
- Java 21 bytecode, Maven, Spigot 1.21.11 API; Java 25 for 26.x compatibility checks/servers. Target: Paper and Spigot 1.21.11–26.3; see `docs/COMPATIBILITY.md`. Root package: `com.Lino.battlePass`.
- Use `rg` for focused searches; avoid reading the entire repository or generated output.
- For bug fixes, reproduce the failure in a regression test where feasible, then run the relevant tests and `mvn clean package`.
- Owner instruction: do not commit or push test files. Newly created regression tests are temporary: after verification, delete their sources and generated classes/reports from the PC before staging changes. Preserve pre-existing tests unless the owner asks to remove them. Summarize the verification in the relevant guide instead.
- Preserve mission progress keys, player data, server configuration and existing GUI ownership checks.
- Update the relevant guide when changing flows, settings, build requirements or invariants.
- Owner instruction: after a task changes source code, commit the completed, verified changes and push to `https://github.com/lino9999/BattlePass` before finishing. Do not leave source fixes local only. This is authorization for routine commits and pushes; no repeated confirmation is needed.
- Check status and the remote branch first. Include only task-related files; preserve other work. Never force-push or rewrite shared history. If push is blocked, report the exact blocker and retain the local commit.
- Never commit credentials, local server databases, downloaded tooling or compiled JARs. Provide the built JAR locally when useful.
- `.gitignore` excludes generated/local files from Git; `.ignore` also reduces searches in tools that support it. These are search aids, not a guarantee that every AI client excludes those files.
