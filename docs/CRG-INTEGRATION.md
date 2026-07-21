# code-review-graph Integration

> **Status**: Active since 2026-07-21 (tooling integration).
> **Version**: code-review-graph 2.3.7, installed via `pipx` (isolated venv at `~/Library/Application Support/pipx/venvs/code-review-graph/`).

`code-review-graph` (CRG) is a knowledge-graph engine that indexes every symbol/edge in a repo and exposes them as MCP tools to AI coding platforms. For Hey Pickler we register **three graphs** (one per subsystem) so review sub-agents can answer impact / dead-code / community questions structurally instead of via file grepping.

## Why three graphs (and not one)

The repo is a monorepo with three subsystems whose code never cross-imports:

| Subsystem | Path | Lang | Nodes | Edges | Files |
|-----------|------|------|-------|-------|-------|
| Server (backend) | `hey-pickler-server/` | java + yaml + sql + lua | ~2364 | ~31600 | ~345 |
| Admin (panel) | `hey-pickler-admin/` | typescript + vue | ~633 | ~5498 | ~98 |
| wxapp (mini-program) | `hey-pickler-wxapp/` | javascript + typescript | ~244 | ~2275 | ~28 |

CRG treats each `--repo` path as an isolated graph. There is no cross-graph join — if you need to answer a cross-subsystem question (e.g., "does the wxapp call the same event API that admin uses?"), query each graph separately and reconcile in the agent's reasoning.

## Architecture

```
pickler/                            # monorepo root
├── .mcp.json                       # ← registers 3 MCP servers (Claude Code reads this on startup)
├── CLAUDE.md                       # ← documents MCP usage (appended, not overwritten)
├── hey-pickler-server/.mcp.json    # ← CRG install artifact (legacy, harmless if present)
└── ~/.code-review-graph/
    ├── watch.toml                  # ← daemon config (3 repos registered)
    └── logs/                       # ← daemon + per-repo watcher logs

~/.local/bin/crg-daemon             # ← multi-repo watcher (PID managed by supervisor)
~/.local/bin/code-review-graph      # ← CLI (build / update / query / impact / detect-changes / serve)
```

### MCP servers (in `.mcp.json` at repo root)

| Name | cwd | Purpose |
|------|-----|---------|
| `crg-server` | `hey-pickler-server` | Java + Spring + MyBatis-Plus indexing |
| `crg-admin` | `hey-pickler-admin` | Vue 3 + TypeScript + Vite indexing |
| `crg-wxapp` | `hey-pickler-wxapp` | WeChat mini-program JS indexing |

After restarting Claude Code, these become available as `mcp__crg-server__<tool>`, `mcp__crg-admin__<tool>`, `mcp__crg-wxapp__<tool>`.

### `crg-daemon` (background watch)

Listens on the 3 registered repo paths with a 2-second poll. When files change, it runs an incremental `update` against that repo's graph (no full re-parse). Daemon lifecycle:

```bash
crg-daemon status       # Show watched repos + PIDs
crg-daemon logs server  # Tail log for one repo (~/.code-review-graph/logs/)
crg-daemon stop         # Stop all watchers
crg-daemon start        # Start (auto-loads ~/.code-review-graph/watch.toml)
crg-daemon restart      # Stop + start
```

## MCP tools (most useful)

All three MCP servers expose the same tool surface; pick the one matching the subsystem you are working on.

| Tool | Use when |
|------|----------|
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword (prefer over Grep) |
| `query_graph_tool` | Tracing `callers_of` / `callees_of` / `imports_of` / `tests_for` / `depends_on` |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding execution paths affected by a change |
| `get_review_context_tool` | Token-efficient source snippets for review |
| `detect_changes_tool` | Risk-scored change analysis (mirror of CLI `detect-changes`) |
| `get_architecture_overview_tool` | High-level structure |
| `list_communities_tool` | Module/cluster boundaries |
| `find_dead_code_tool` | Functions/classes with no callers and no test refs |
| `find_large_functions_tool` | Oversized symbols (refactor candidates) |

Full tool list lives in `.claude/skills/` per CRG install — explore with `MCP__crg-server__<tab>` in Claude Code.

## Workflows

### Code review

1. Agent receives a diff (or PR base ref).
2. Call `mcp__crg-<subsystem>__detect_changes_tool` (or CLI `code-review-graph detect-changes --repo=<path> --base=<base> --brief --churn`).
3. The output includes a risk-scored review priority list, token savings estimate vs full read, and affected flows — feed those into the review checklist.

### Impact analysis before refactor

1. Identify the symbol (file + function/class).
2. Call `mcp__crg-<subsystem>__get_impact_radius_tool` to see all callers and dependents.
3. Call `mcp__crg-<subsystem>__get_affected_flows_tool` for end-to-end execution paths.
4. Cross-check `mcp__crg-<subsystem>__find_dead_code_tool` to spot orphan code paths.

### Architecture questions

1. `mcp__crg-<subsystem>__get_architecture_overview_tool` gives high-level module map.
2. `mcp__crg-<subsystem>__list_communities_tool` reveals community clusters.
3. Drill down with `query_graph_tool pattern="imports_of"` on the entry point of a community.

## CLI quick reference

```bash
# Build / re-parse
code-review-graph build --repo=<path>           # full re-parse (slow, ~7s for server)
code-review-graph update --repo=<path>          # incremental (auto-runs on file change via daemon)
code-review-graph update --repo=<path> --brief  # re-parse + show impact summary

# Analysis (read-only against existing graph)
code-review-graph status --repo=<path>          # node/edge counts
code-review-graph query --repo=<path> pattern=callers_of <symbol>
code-review-graph impact --repo=<path> <file>
code-review-graph dead-code --repo=<path>
code-review-graph large-functions --repo=<path>
code-review-graph detect-changes --repo=<path> --base=<ref> --brief --churn
code-review-graph detect-changes --repo=<path> --base=<ref> --verify  # calibrated token count

# Multi-repo registry
code-review-graph register <path> --alias=<name>
code-review-graph unregister <path-or-alias>
code-review-graph repos

# Daemon
crg-daemon add <path>
crg-daemon remove <path>
crg-daemon start | stop | restart | status | logs

# MCP install / uninstall
code-review-graph install --platform=claude-code --repo=<path>   # writes .mcp.json + .claude/skills
code-review-graph uninstall
```

## Installation / setup (re-doing this from scratch)

```bash
# 1. Install pipx (one-time, requires Homebrew)
brew install pipx && pipx ensurepath

# 2. Install CRG in isolated venv
pipx install code-review-graph

# 3. Confirm
code-review-graph --version   # 2.3.7+
crg-daemon --help

# 4. Build initial graphs (one-time, takes ~15s total)
code-review-graph build --repo=hey-pickler-server --skip-flows --skip-postprocess
code-review-graph build --repo=hey-pickler-admin  --skip-flows --skip-postprocess
code-review-graph build --repo=hey-pickler-wxapp  --skip-flows --skip-postprocess

# 5. Register in multi-repo registry + daemon
code-review-graph register hey-pickler-server --alias=pickler-server
code-review-graph register hey-pickler-admin  --alias=pickler-admin
code-review-graph register hey-pickler-wxapp  --alias=pickler-wxapp
crg-daemon add hey-pickler-server
crg-daemon add hey-pickler-admin
crg-daemon add hey-pickler-wxapp
crg-daemon start

# 6. MCP config is already in /Users/muxi/workspace/pickler/.mcp.json
#    (committed in this repo). Restart Claude Code to pick up the 3 servers.

# 7. Optional: rebuild postprocess (flows, communities, FTS) for richer queries
code-review-graph postprocess --repo=hey-pickler-server
code-review-graph postprocess --repo=hey-pickler-admin
code-review-graph postprocess --repo=hey-pickler-wxapp
```

## Operational notes

- **Graph staleness**: daemon polls every 2s, incremental update ~100ms for typical edits. If a large rebase happens, force a full re-parse: `code-review-graph build --repo=<path>`.
- **Memory footprint**: each graph is a SQLite DB at `<repo>/.code-review-graph/graph.db`. Server graph ~10MB; admin ~2MB; wxapp ~1MB.
- **`--skip-flows` / `--skip-postprocess`**: these speed up the initial build by skipping flow detection, community clustering, and FTS indexing. After the graph is built, run `code-review-graph postprocess --repo=<path>` to populate those tables for richer queries (`list_communities_tool`, full-text search).
- **MCP server count vs tool namespace pollution**: 3 servers × ~10 tools = ~30 tools in the agent's tool list. Consider using `--tools` on `serve` to limit exposure if context is tight.
- **Daemon survivability**: `crg-daemon` uses Python's daemonization, not launchd. It will NOT auto-start on reboot. Add a LaunchAgent plist if the workstation reboots frequently and you want background watch.
- **Server graph nodes include `kind='Class'`, `'Function'`, `'Test'` for Java** — Spring DI resolver adds CALLS edges for `@Autowired` / `@Resource` injection, so query_graph_tool's `callers_of` finds indirect dependencies through the container, not just lexical calls.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `mcp__crg-server__*` tools missing in Claude Code | `.mcp.json` not picked up | Restart Claude Code; verify `.mcp.json` exists at repo root with `jq . .mcp.json` |
| Graph reports 0 nodes for a subsystem | Stale or never-built graph | `code-review-graph build --repo=<path>` |
| `detect-changes` returns 0 changed functions | Subdir `--base` resolves against root git, not the subdir's logical history | Use `--base=<branch>` or a specific commit SHA; verify with `git log --oneline <base>..HEAD -- <subsystem-prefix>` |
| `crg-daemon start` says "already running" but no logs | Stale PID file | `crg-daemon stop && rm ~/.code-review-graph/daemon.pid && crg-daemon start` |
| `uvx` first-run takes ~30s | uvx is downloading the package into its cache | Subsequent runs are ~200ms |

## What this is NOT

- CRG is **not** a code-style reviewer or a lint replacement. It's a structural navigation tool.
- CRG does **not** analyze runtime behavior, only static call/import edges.
- CRG does **not** index git history unless `--churn` is passed to `detect-changes`. Adding `--churn` triggers a 90-day commit count per file (configurable via `CRG_CHURN_WINDOW_DAYS`).
- The multi-repo setup has **no cross-graph joins**. For end-to-end traces spanning subsystems (e.g., admin click → server endpoint → DB query), use Claude Code's regular file reading instead of the MCP graph tools.