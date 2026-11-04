# Assay — repository review, 4 Nov 2026

Scope: every branch of `mbaliga/assay`, read against the console source, the JVM engine, CI, and the repo's own ledgers (`BUILD-SPEC.md`, `docs/STATUS.md`).

## 1. State of the branches

| Branch | Ahead/behind `main` | Contains | Verdict |
|---|---|---|---|
| `main` | — | `LICENSE`, `cleanup-artifacts.yml`, `docs/`. **No code.** | Decoy. Nothing a reader can run. |
| `ccr-4d4cf4bc-rvt28h` (3 Nov) | +72 / 0 | WP0 "one trunk": merges of `fonebrew-development`, `fonebrew-execution-contract`, porting plan, one cherry-pick; `CLAUDE.md`, `STATUS.md`, `BUILD-SPEC.md`; toolchain lockstep; debug APK in `dist/` | **The real head.** No PR to `main`. |
| `claude/fonebrew-development-clzu43` | +55 / −4 | Ratified `.assay/` contract, dead `MainActivity` removed, setup-android v4 | Fully inside `ccr-*`. |
| `feat/fonebrew-execution-contract` | +58 / −4 | Execution-evidence boundary + acceptance test | Fully inside `ccr-*`. |
| `claude/multi-platform-porting-plan-nnvjug` | +2 / 0 | `docs/PORTING-PLAN.md` | Inside `ccr-*`. |
| `claude/asystemofcells-redesign-s6yv4c` | +1 / 0 | PolyForm Noncommercial relicense | **Owner decision pending.** Left unmerged on purpose. |
| `claude/repo-status-overview-2c0rgm` | +56 / −4 | Hyle submodule + AGP downgrade + one doc | Do not merge; the doc was cherry-picked. Superseded. |
| `agent/assay-v1-foundation` | +50 / −4 | Aug "1.1" console + docs | Superseded. |
| `apk-testbuild`, `claude/github-storage-cleanup-*` | +1 | Test APK / cleanup workflow | Superseded. |

`android-console.yml` triggers only on `main` and `agent/**`; `ci.yml` was extended to `ccr-*`. The console's "assembleDebug lintDebug exit 0" in `STATUS.md` is therefore a local claim, never a CI run.

## 2. What exists, what doesn't

**Exists and is good.** The JVM trust engine: ~30 files, 11 acceptance harnesses, contract validator, SARIF 2.1.0 normalise/merge, fingerprinting, redaction, fail-closed bus, `GitBusPublisher` with `--force-with-lease`, a candidate state machine where only a `human:` actor can approve, `ProofGate` judging exit codes only. This is a trust engine with no product on top.

**Does not exist** (verified against source, not the ledger):

- Console has **no `INTERNET` permission and no network code**. It cannot connect to Git/GitHub, start a run, read a result, or approve a candidate. "Open verified audit" means *import a JSON file someone else produced*.
- **No on-ramp.** Home is three buttons and one sentence of intro copy. Nothing explains runner → bus → candidate → Fonebrew hand-off. The "sample project" is `DemoData.kt`.
- **No end-to-end `assay audit` command.** Thirty subcommands chained by hand; the internal bus still wraps output in a `fixture-1` artifact shim.
- **No AI layer** (`ai/` package absent), **no generated proving tests**, **no `actions/audit.yml` / `prove.yml` / `approve.yml`**, **no sample vulnerable repo**.
- Console is Views-based. The Compose + Hyle rewrite did not happen. No `HyleTokens.kt`.
- No release signing, no PAT storage, no model-provider settings.

`BUILD-SPEC.md` §0 says exactly this in its own words. `docs/STATUS.md`: "WP1–WP7: NOT STARTED".

## 3. Verdict

**Not production-ready. Roughly WP0 of 8.** The gap is specified to the file level; what is missing is execution. The product documents under `docs/` (personas, IA, journeys, v2 roadmap) describe a v2 that was never started, and they describe it in *role* terms (maintainer, reviewer, operator). They do not describe *behaviour* — who panics, who delegates, who distrusts — which is what the console's on-ramp has to be designed for. `03-ARCHETYPES-AND-JOURNEYS.md` adds that.

## 4. Recommended immediate actions

1. Open `ccr-4d4cf4bc-rvt28h → main` as a PR so CI runs and `main` stops lying. Add `ccr-*` and `design/*` to `android-console.yml` triggers.
2. Decide the licence question once for the constellation, then merge or delete `claude/asystemofcells-redesign-s6yv4c`.
3. Delete the superseded branches listed in `STATUS.md`.
4. Execute `05-ULTRACODE-PROMPT.md`, which re-sequences BUILD-SPEC's WP1–WP7 around the four lenses in this folder (on-ramp and models room pulled forward; findings still never get a browser).

## 5. The four lenses (this folder)

| File | Lens |
|---|---|
| `01-COMPETITIVE-LANDSCAPE.md` | Best-in-class across desktop/CI, mobile, agentic — and where nobody is |
| `02-ON-DEVICE-INTELLIGENCE.md` | Model catalogue, t-shirt sizes, one-click download, bring-your-own-model, BYOK |
| `03-ARCHETYPES-AND-JOURNEYS.md` | Behavioural archetypes (not roles), their journeys and expectations |
| `04-ROOMS-UX.md` | The console redrawn on Fonebrew's room grammar |
| `05-ULTRACODE-PROMPT.md` | The executable prompt for a Sonnet Claude Code session |
