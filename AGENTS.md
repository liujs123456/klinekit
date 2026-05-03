# klinekit — Java crypto strategy backtester

You're working on **klinekit**, a Java backtest engine for crypto trading strategies (spot DCA + perpetual contracts). This is the user's first independent Java project (they've done Java in UCI coursework but want a real portfolio piece).

## Read first

Before doing anything, read the user's memory files at `~/.claude/projects/-Users-andrewsmac2-Desktop-klinekit/memory/`. They contain:

- `klinekit_design.md` — full architecture, milestones, scope, why each design choice
- `portfolio_context.md` — what other projects the user has shipped, so you don't duplicate
- `user_profile.md` — UCI student building resume projects
- `working_style.md` — how the user wants you to operate (autonomy, pace, security tradeoffs)

## House rules for this project

- **Java 21** — use modern features: records, sealed interfaces, pattern matching, virtual threads when appropriate. The point is to look like 2026 Java, not 2015 Java.
- **Gradle** (Kotlin DSL preferred). Maven is out unless there's a real reason.
- **Spring Boot 3.x** for the API layer — but core engine stays Spring-free so it can ship as a standalone jar.
- **No Lombok in core/** — use records. Lombok is OK in api/ and persistence/ if it cuts real boilerplate.
- **JUnit 5 + AssertJ** for tests. **Testcontainers** for integration tests touching Postgres.
- **Conventional commits** (feat:, fix:, test:, chore:, docs:, refactor:). Helps when generating CHANGELOGs later.
- **Module layout**: `core`, `api`, `persistence`, `cli`, `strategies` — see klinekit_design.md.

## What NOT to do

- Don't pull in random 3rd-party deps without flagging — minimal dep footprint is part of the polish.
- Don't write strategies that look like crypto-dca-monitor (Python, stdlib-only). This is a different project: typed engine, not a cron script.
- Don't auto-generate boilerplate Spring code without explaining the architecture choice — that's the part the user wants to learn.

## Milestone gating

Stay inside the active milestone. The user wants ship-able cuts at M1, M2, M3 — not one giant PR. After each milestone: typecheck, tests pass, commit, push, ask before starting next.
