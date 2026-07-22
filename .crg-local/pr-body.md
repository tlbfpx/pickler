## Summary

Wire `code-review-graph` (CRG) 2.3.7 as a multi-repo MCP knowledge-graph engine for code review and impact analysis. Each of the three subsystems (`hey-pickler-server` / `hey-pickler-admin` / `hey-pickler-wxapp`) gets its own graph so review sub-agents can answer structural questions (callers, impact, dead code, communities) without grep-based file exploration.

## Why

Review sub-agents in this monorepo currently have to fall back to `Grep`/`Read` to navigate a 67MB Java backend, a 331MB Vue admin panel, and a 17MB wxapp. That is slow, expensive in tokens, and structurally shallow — it misses indirect callers (Spring DI), community boundaries, and dead-code paths. CRG indexes every symbol + edge into a SQLite knowledge graph and exposes ~10 MCP tools per graph, letting agents ask "what's the blast radius of changing `PlacementService.issue`?" or "which functions in `wxapp/utils/request.js` have no callers?" in one round-trip.

## Architecture

- **Root `.mcp.json`** registers 3 MCP servers (`crg-server` / `crg-admin` / `crg-wxapp`); each runs `code-review-graph serve --repo=<subdir>` with the subdir as `cwd`. Claude Code picks up all 3 on startup.
- **Per-subsystem `.mcp.json` + `.claude/skills/`** are committed so agents working inside a subsystem get the same MCP tools (consistent UX).
- **`crg-daemon`** runs in the background watching all 3 repos with a 2s poll; file changes trigger incremental `update` (no full re-parse).
- **Multi-repo registry** (`code-review-graph register`) lists the 3 paths under aliases `pickler-server` / `pickler-admin` / `pickler-wxapp`.

## Graph sizes (post-build)

| Subsystem | Files | Nodes | Edges | Languages |
|-----------|------:|------:|------:|-----------|
| hey-pickler-server | 345  | 2364  | 31600 | java, yaml, sql, lua |
| hey-pickler-admin  |  98  |  633  |  5498 | typescript, vue     |
| hey-pickler-wxapp  |  28  |  244  |  2275 | javascript, typescript |

## Changes (across 2 commits)

- **`.mcp.json`** (root) — new, 3 MCP server entries.
- **`docs/CRG-INTEGRATION.md`** — new, 189-line ops runbook (architecture, install, CLI reference, troubleshooting).
- **`docs/CRG-PR-TEMPLATE.md`** — new, copy-paste PR description templates for future CRG-related PRs.
- **`CLAUDE.md`** — appended 38-line "MCP Tools: code-review-graph" section (original 172-line project overview preserved).
- **`hey-pickler-{server,admin,wxapp}/.mcp.json`** — new per-subsystem CRG install artifacts.
- **`hey-pickler-{server,admin,wxapp}/.claude/skills/`** — new, 4 generated skills per subsystem: `debug-issue` / `explore-codebase` / `refactor-safely` / `review-changes`.
- **`hey-pickler-{server,admin,wxapp}/.gitignore`** — added `.code-review-graph/` to keep SQLite graph DBs out of git.
- **Root `.gitignore`** — added `.crg-local/` for local-only scratch files.

## Tooling layout

- Installed via `pipx` (isolated venv at `~/Library/Application Support/pipx/venvs/code-review-graph/`).
- Binaries: `~/.local/bin/code-review-graph`, `~/.local/bin/crg-daemon`.
- Pre-commit hook at `.git/hooks/pre-commit` runs `code-review-graph update` + `detect-changes --brief` on every commit; remove the hook if graph staleness is acceptable.

## Test plan

- [x] `code-review-graph build --repo=hey-pickler-server` succeeds (7s, 2364 nodes)
- [x] `code-review-graph build --repo=hey-pickler-admin` succeeds (3.7s, 633 nodes)
- [x] `code-review-graph build --repo=hey-pickler-wxapp` succeeds (3.6s, 244 nodes)
- [x] `crg-daemon start` — daemon alive, 3 watchers (PIDs 70030/70031/70032)
- [x] Smoke test: `uvx code-review-graph serve --repo=hey-pickler-server` boots, installs 74 packages on cold start, stable under timeout
- [x] MCP integration: root `.mcp.json` parsed, 3 servers registered
- [x] `npm run lint:check` in admin — green (no JS changes)
- [ ] **TODO before merge**: reviewer to restart Claude Code in this branch and confirm `mcp__crg-server__<tool>` / `mcp__crg-admin__<tool>` / `mcp__crg-wxapp__<tool>` appear in the agent tool list
- [ ] **TODO before merge**: reviewer to run `code-review-graph query --repo=hey-pickler-server pattern=callers_of PlacementService` and confirm non-empty (validates graph integrity)

## Risks

- **Pre-commit hook slowdown**: adds ~200-500ms per commit (incremental update). Mitigation: the hook uses `|| true` so failures don't block; remove via `rm .git/hooks/pre-commit` if undesired.
- **Tool namespace bloat**: ~30 MCP tools in agent context. Mitigation: if context gets tight, use `serve --tools=query_graph_tool,...` to restrict exposure per server.
- **Cross-graph queries unsupported**: CRG treats each `--repo` as isolated; agents working cross-subsystem must query each graph separately. Documented in `docs/CRG-INTEGRATION.md`.
- **Daemon survivability**: not managed by launchd; won't auto-start on reboot. Workstation-only concern; CI doesn't depend on it.

## Rollout

- [ ] Merge → master
- [ ] Anyone working in this repo: restart Claude Code to pick up the 3 MCP servers.
- [ ] Anyone running `mvn test` or `npm run dev` locally: no action needed (graphs are dev-only).

## Follow-ups (not in this PR)

- Wire `mcp__crg-server__find_dead_code_tool` output into the weekly tech-debt review (separate change).
- Add LaunchAgent plist for `crg-daemon` on macOS workstations (separate infra change).
- Evaluate CRG `--embedding-provider` for semantic code search once we have an OpenAI API key in dev env.