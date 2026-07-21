# PR Description Templates for CRG-related PRs

> Copy-paste source for PR descriptions when adding CRG-aware features, extending the multi-repo setup, or wiring CRG into new workflows.

---

## Template A — Adding a new CRG-powered workflow (e.g., new MCP tool integration, new `crg-daemon` repo)

```markdown
## Summary

<1-2 sentence what this PR adds>

<!-- Example:
Wire the `mcp__crg-server__find_dead_code_tool` output into the weekly
tech-debt report cron so the report author gets an auto-prioritized list
of dead code paths to consider for cleanup.
-->

## Why

<1-2 sentences on the motivation; reference the issue or OpenSpec change>

## Changes

- `<file>` — <what changed and why>
- `<file>` — <what changed and why>

## CRG integration points

- New MCP tools used: `<list>` (e.g., `mcp__crg-server__get_impact_radius_tool`)
- Affected graphs: `<list>` (e.g., `hey-pickler-server`)
- Graph rebuild required: yes/no — if yes, run `code-review-graph build --repo=<path>`

## Test plan

- [ ] Unit tests: `<path>` — covers `<scenario>`
- [ ] Manual smoke: `<what you ran>` — `<result>`
- [ ] CRG sanity check: `code-review-graph status --repo=<path>` — graph still healthy post-change
- [ ] (if detect-changes is in the loop) `code-review-graph detect-changes --repo=<path> --base=master --brief --churn` — review priorities still computed

## Rollout

- [ ] Daemon restart required: yes/no
- [ ] MCP server restart required (Claude Code reload): yes/no
- [ ] Documentation update: `<path>` (e.g., `docs/CRG-INTEGRATION.md`)

## Risks

- <risk 1 + mitigation>
- <risk 2 + mitigation>
```

---

## Template B — Subsystem-level CRG install / config change (e.g., adding a new subsystem to the multi-repo registry)

```markdown
## Summary

<1-2 sentence what subsystem / path is being registered>

<!-- Example:
Register `hey-pickler-bot/` as the 4th CRG graph so the future Discord bot
gets the same structural review tooling as the existing three subsystems.
-->

## Why

<1-2 sentences>

## Changes

- `<subdir>/.mcp.json` — new (CRG install artifact)
- `<subdir>/.claude/skills/*` — new (4 CRG skills: debug-issue / explore-codebase / refactor-safely / review-changes)
- `<subdir>/.gitignore` — `.code-review-graph/` added
- `<subdir>/.code-review-graph/` — gitignored; do not commit
- Root `.mcp.json` — new `<name>` server entry pointing at `<subdir>`
- Root `CLAUDE.md` — MCP Tools section updated with the new graph's stats

## Daemon registration

```bash
code-review-graph register <subdir> --alias=pickler-<short-name>
crg-daemon add <subdir>
crg-daemon restart
crg-daemon status   # confirm new entry shows alive
```

## Graph stats (post-build)

| Subsystem | Files | Nodes | Edges | Languages |
|-----------|-------|-------|-------|-----------|
| `<subdir>` | <N> | <N> | <N> | <langs> |

## Test plan

- [ ] `code-review-graph build --repo=<subdir>` succeeds
- [ ] `code-review-graph status --repo=<subdir>` shows the expected node/edge counts
- [ ] `crg-daemon status` lists the new path with `alive`
- [ ] Restart Claude Code; verify `mcp__crg-<short-name>__<tool>` appears in tool list
- [ ] Manual smoke: `mcp__crg-<short-name>__semantic_search_nodes_tool query="..."` returns expected hits

## Rollout

- [ ] `docs/CRG-INTEGRATION.md` updated (table + setup steps)
- [ ] Team notification: anyone using this subsystem should restart Claude Code
```

---

## Template C — Changing CRG config that affects all subsystems (e.g., daemon timeout, postprocess settings)

```markdown
## Summary

<1-2 sentence what global config changed>

<!-- Example:
Enable FTS5 full-text search postprocess on all 3 graphs (was previously
skipping postprocess for speed; new build pipeline budget allows it).
-->

## Why

<motivation>

## Changes

- `<file>` — <what>
- `<file>` — <what>

## Affected graphs

All three: `hey-pickler-server` / `hey-pickler-admin` / `hey-pickler-wxapp`

## Test plan

- [ ] For each affected subsystem:
  - [ ] `code-review-graph build --repo=<path>` (full rebuild with postprocess)
  - [ ] `code-review-graph status --repo=<path>` — node count reasonable
  - [ ] `code-review-graph query --repo=<path> pattern="..."` returns hits
- [ ] `crg-daemon restart` and confirm all watchers alive

## Risks

- Build time impact: <estimate>
- Disk usage impact: <estimate per graph>
- Rollback: <how to revert>

## Rollout

- [ ] Daemon restart required
- [ ] MCP server reload (Claude Code restart) required
- [ ] Team notification
```

---

## Pre-flight checklist (any CRG-related PR)

Run before requesting review:

- [ ] `crg-daemon status` — all watchers alive
- [ ] `code-review-graph status --repo=<each affected path>` — node/edge counts sane
- [ ] `code-review-graph detect-changes --repo=<each affected path> --base=<base> --brief --churn` — review priority list populated
- [ ] No `.code-review-graph/` directories accidentally staged
- [ ] No pre-commit hook concerns (check `.git/hooks/pre-commit` is intentional)
- [ ] `docs/CRG-INTEGRATION.md` updated if the change affects documented setup or ops