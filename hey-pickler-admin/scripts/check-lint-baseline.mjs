#!/usr/bin/env node
/**
 * Lint baseline check.
 *
 * Reads the captured lint baseline at `docs/superpowers/lint-baseline.json`
 * (a flat array of {file,line,col,rule,message} entries captured at a known-
 * good state) and compares it against the current `eslint --format json`
 * output. Exits non-zero if there are NEW warnings — i.e. file:line:col
 * entries present in the current run but NOT in the baseline.
 *
 * Use after `npm run lint:check` (or as a CI step) to enforce the
 * "no-new-warnings" discipline documented in docs/superpowers/lint-baseline.md.
 *
 * Suppressions are NOT accepted: every new warning is an error. To
 * legitimately add a new `as any`, regenerate the baseline (see
 * `npm run lint:baseline:update` below) and commit the diff.
 *
 * Exit codes:
 *   0 — no new warnings (baseline unchanged or fewer)
 *   1 — new warnings introduced
 *   2 — script-level error (eslint not found, baseline unreadable, etc.)
 */

import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve, relative, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ADMIN_ROOT = resolve(__dirname, '..')              // hey-pickler-admin/
const REPO_ROOT = resolve(ADMIN_ROOT, '..')              // pickler/
const BASELINE_PATH = resolve(REPO_ROOT, 'docs/superpowers/lint-baseline.json')
const ESLINT_BIN = resolve(ADMIN_ROOT, 'node_modules/.bin/eslint')

const ESLINT_ARGS = [
  '.',
  '--ext', '.vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts',
  '--format', 'json',
  '--ignore-path', '.gitignore',
  '--no-error-on-unmatched-pattern',
]

/** Convert one eslint result row to a flat baseline entry. */
function toEntry (result, msg) {
  // eslint paths are relative to the cwd it was invoked in.
  // We invoke from ADMIN_ROOT, so paths are relative to that.
  return {
    file: relative(REPO_ROOT, resolve(ADMIN_ROOT, result.filePath)).replace(/\\/g, '/'),
    line: msg.line,
    col: msg.column,
    rule: msg.ruleId,
    message: msg.message.replace(/\s+/g, ' ').trim().slice(0, 160),
  }
}

function loadBaseline () {
  if (!existsSync(BASELINE_PATH)) {
    console.error(`✗ Baseline not found: ${BASELINE_PATH}`)
    console.error('  Run `npm run lint:baseline:update` to create it.')
    process.exit(2)
  }
  try {
    return JSON.parse(readFileSync(BASELINE_PATH, 'utf8'))
  } catch (e) {
    console.error(`✗ Failed to parse ${BASELINE_PATH}: ${e.message}`)
    process.exit(2)
  }
}

function runEslint () {
  if (!existsSync(ESLINT_BIN)) {
    console.error(`✗ eslint not found at ${ESLINT_BIN}. Did you run \`npm install\`?`)
    process.exit(2)
  }
  let out
  try {
    out = execFileSync(ESLINT_BIN, ESLINT_ARGS, {
      cwd: ADMIN_ROOT,
      encoding: 'utf8',
      maxBuffer: 32 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (e) {
    // eslint exits non-zero when there are warnings/errors; JSON output is on stdout.
    if (e.stdout) out = e.stdout
    else {
      console.error(`✗ eslint failed to run: ${e.stderr || e.message}`)
      process.exit(2)
    }
  }
  let results
  try { results = JSON.parse(out) } catch (e) {
    console.error('✗ Failed to parse eslint JSON output:', e.message)
    console.error(out.slice(0, 500))
    process.exit(2)
  }
  return results
}

const mode = process.argv[2]
if (mode === 'update') {
  const results = runEslint()
  const entries = []
  for (const r of results) for (const m of r.messages) entries.push(toEntry(r, m))
  entries.sort((a, b) => a.file.localeCompare(b.file)
    || a.line - b.line || a.col - b.col || a.rule.localeCompare(b.rule))
  writeFileSync(BASELINE_PATH, JSON.stringify(entries, null, 2) + '\n')
  console.log(`✓ Wrote ${entries.length} entries to ${relative(REPO_ROOT, BASELINE_PATH)}`)
  process.exit(0)
}

if (mode !== 'check' && mode !== undefined) {
  console.error(`Unknown mode: ${mode}. Use \`check\` (default) or \`update\`.`)
  process.exit(2)
}

// === check mode (default) ===
const baseline = loadBaseline()
const baselineSet = new Set(baseline.map(e => `${e.file}:${e.line}:${e.col}:${e.rule}`))
const results = runEslint()

const current = []
for (const r of results) for (const m of r.messages) current.push(toEntry(r, m))
const currentSet = new Set(current.map(e => `${e.file}:${e.line}:${e.col}:${e.rule}`))

const newOnes = current.filter(e => !baselineSet.has(`${e.file}:${e.line}:${e.col}:${e.rule}`))
const fixed = baseline.filter(e => !currentSet.has(`${e.file}:${e.line}:${e.col}:${e.rule}`))

if (newOnes.length === 0) {
  console.log(`✓ No new lint warnings (baseline: ${baseline.length}, current: ${current.length}, fixed: ${fixed.length})`)
  process.exit(0)
}

console.error(`✗ ${newOnes.length} new lint warning(s) introduced (baseline: ${baseline.length}, current: ${current.length}):`)
for (const e of newOnes) {
  console.error(`  ${e.file}:${e.line}:${e.col}  ${e.rule}  ${e.message}`)
}
if (fixed.length) {
  console.error(`  (${fixed.length} entries from baseline are now fixed — run \`npm run lint:baseline:update\` to refresh)`)
}
process.exit(1)
