# CLAUDE.md

The engineering constitution for this project lives in **[`AGENTS.md`](./AGENTS.md)** — stack,
package layout, the concurrency/locking rules, money-handling discipline, error contract, API
conventions, testing requirements, and commit discipline. Read it before writing or reviewing any
code here. It is written tool-agnostically on purpose so it works as documentation regardless of
which agent or human is reading it; this file adds only what is specific to working in Claude Code.

## Claude Code specifics for this repo

- **Toolchain**: JDK 25 is pinned via `JAVA_HOME` in `~/.zshrc`. Use `./mvnw`, never a bare `mvn`.
- **Subagents to use, and when:**
  - `.claude/agents/concurrency-auditor` — run this against every commit that touches
    `booking`, `show`'s `show_seat` locking queries, or the `notification` outbox, before
    treating that commit as done. It specifically hunts the four traps listed in `AGENTS.md` §9.2.
  - `.claude/agents/spring-reviewer` — run this against any commit for a general pass on
    package boundaries, N+1 risk, and transaction-boundary correctness.
- **Skills to load proactively** (don't wait to be asked): `concurrency-testing` before writing
  any test with threads/latches; `flyway-migrations` before adding or editing a `V*__*.sql` file;
  `api-contract` before adding a new endpoint or exception type.
- **Plan file**: the approved architecture and phase/commit plan is at
  `docs/raw/planning/architecture-plan.md` (once committed to the repo) — treat it as the
  authoritative build order; don't reorder phases without flagging it to the user first, since
  the phase ordering itself (guardrails → foundation → concurrency core → money → async → demo
  layer) is part of what's being evaluated.
