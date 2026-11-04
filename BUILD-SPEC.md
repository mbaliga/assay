# Assay — Production Build Spec for Claude Code

> Paste this file into the repo root of `mbaliga/assay` as `BUILD-SPEC.md` and start the session with: *"Read BUILD-SPEC.md. Execute WP0, stop at its gate."* Then one WP per session.
>
> Session settings: **model `sonnet`**, **`/effort high`** (use `xhigh` for WP0 and WP4). Keep the session in `plan` mode for the first 10 minutes of each WP, then implement. Run `/code-review` before claiming any WP done.

---

## 0. What you are building, in one paragraph

Assay is a security-audit pipeline for Android repos, wedged on AI/vibe-coded apps. **Four deterministic scanners (Gitleaks, Semgrep, OSV-Scanner, MobSF) are the only things that may create a finding.** AI (on-device via ASOM, or a user-supplied cloud key) may only triage, explain, propose a patch and write a proving test *for a finding that already exists*. Every patch is a `candidate` that cannot reach `approved` without a proving test that **fails on the unpatched code and passes on the patched code, by exit code, run twice each** (`ProofGate`, already in the code). Output lands in the scanned repo on a **user-selected branch** under `.assay/` in the ratified shape Fonebrew Core reads. The phone app ("console") is a control plane — start a run, watch it, approve or reject a candidate — plus an offline local APK quick-check. It is never a findings dashboard; Fonebrew's Audit tab is.

The engine already exists (~90 files, 11 acceptance-test harnesses, CLI with 30 subcommands). What is missing is: one trunk, an end-to-end `audit` command, a runner you can actually use today, the AI layer, generated+executed proving tests, and a console that controls instead of copies commands. That is this spec.

---

## 1. Hard rules (read before every WP)

These are the product. Violating one is a failed WP even if everything "works".

1. **Scanner-only findings.** No code path may construct a `Finding` except the four scanner normalisers and the existing test fixtures. The AI adapter's return types must be structurally unable to carry a finding (it returns `Triage`, `Explanation`, `PatchProposal`, `ProvingTestSource` — never `Finding`). Add a test (`AiCannotOriginateFindingsTest`) that greps `src/main` for `Finding(` constructor calls and fails if any appear outside `Scanner.kt`, `MobSfExecution.kt`, `Sarif.kt`.
2. **Proof is exit codes, never judgment.** `ProofGate` (`Proof.kt`) is the only arbiter. Nothing may set a candidate to `proof_passed` except `candidate-proof` consuming an attempts file produced by an isolated test execution. No "LLM says it looks fixed".
3. **Approve/reject requires a `human:` actor** (already enforced in `Candidate.kt`). The console's Approve button must resolve to that path with a real identity, never `system:*`.
4. **The Kotlin core never executes the scanned repo's build scripts in-process** (THREAT-MODEL). Scanner runs and proving-test runs happen in an isolated job (CI job or sandboxed subprocess), and the core only reads their outputs.
5. **No telemetry, no analytics, no phoning home, ever.** The only network calls are: scanner tool downloads pinned by SHA-256, git push/fetch to the user's remote, GitHub API calls the user explicitly tapped, and model calls to ASOM (`127.0.0.1:11435`) or a cloud provider the user configured. Every cloud provider is a visibly-marked "watched object" in the UI (glyph + label, never colour alone).
6. **Linked libraries are permissive-only** (Apache/MIT/BSD/ISC/CC0/Unlicense; MPL-2.0 case by case). Copyleft and LGPL are banned for linking. Scanner binaries are runner-invoked tools and may carry any licence (MobSF's GPL is fine as a container).
7. **Colour is never the sole carrier of state** (the owner is red-green colourblind). SEV1/SEV2 vs SEV3/SEV4, rejected vs approved: shape + label + luminance, with the Hyle palette (§6). The current `AssayHomeActivity.kt:663-683` red/green chips are a known violation to remove.
8. **Anti-overclaim ladder.** Every WP report ends with exactly one of: `code-complete` (compiles, unit/acceptance tests green), `verified` (ran end-to-end on CI/hosted runner with evidence attached), `ready-for-owner-review` (verified + a device/owner step is the only thing left). You never write "done", "production-ready" or "works on device". The build container has no device: on-device behaviour is **owner-verified only**. Mark such rows `NEEDS-DEVICE-VALIDATION`.
9. **Environment honesty: stubbed, not faked.** If a step cannot run here (no podman, no device, no secret), the code path must fail loudly with a typed error, never return success with placeholder data. `DemoData.kt` stays, but every surface showing it carries the SAMPLE label it already has.
10. **Two contracts, keep them straight.** External = `.assay/` ratified shape (Core's `ASSAY_REPO_CONTRACT_V1.md`, mirrored in `schemas/integrations/`). Internal = trust-engine bus 1.1.0 (`index.json`, `runs/<id>/…`, candidates). The internal bus is Assay's working state; the ratified tree is the projection Core reads. Never merge them.
11. **Do not rename the app, package, or branch names.** Working name stays `Assay`, console package stays `dev.assay`. Do not touch `LICENSE` (see §2.3).
12. **Do not add the Hyle Design System submodule** (owner question OQ-29 is open and the composite build was never green). Copy the token *values* into a local `HyleTokens.kt` (§6).

---

## 2. Owner-supplied inputs (ask for these at the start of WP0, then never block on them)

Collect with `AskUserQuestion`; if an answer is not given, proceed and record the item as `OWNER-ACTION-NEEDED` in `docs/STATUS.md`.

| Input | Used by | If absent |
|---|---|---|
| **2.1** GitHub fine-grained PAT (Contents: read/write, Actions: write, on the repos to be scanned and on `mbaliga/assay`) — stored in the console's Keystore, never in the repo | WP3 console dispatch, WP5 approve | Console runs in "offline / snapshot-only" mode |
| **2.2** The first target repo to audit end-to-end (default: a deliberately vulnerable sample repo you create under `fixtures/sample-repo/` and push as `mbaliga/assay-sample-target` — see WP2) | WP2 verification | Use the fixture repo |
| **2.3** Licence decision: keep **Apache-2.0** on trunk (default — matches Fonebrew Core and the owner's stated FSF/EFF stance, Oct 2026), or merge `origin/claude/asystemofcells-redesign-s6yv4c` (PolyForm Noncommercial 1.0.0 + COMMERCIAL-LICENSE.md, authored Oct 8) | WP0 | Keep Apache-2.0; leave the branch unmerged and note it |
| **2.4** Cloud model key(s) for the AI adapter (Anthropic / OpenAI-compatible / Gemini / DeepSeek / Grok) — optional; ASOM local is the default | WP4 | AI layer runs against ASOM only; cloud shows "not configured" |
| **2.5** Release signing keystore for the console APK | WP6 | Ship debug-signed sideload build; mark release signing `OWNER-ACTION-NEEDED` |

---

## 3. Architecture decisions (final — do not re-derive)

### 3.1 Repo layout after WP0

```
assay/
  BUILD-SPEC.md                 this file
  CLAUDE.md                     short: gates, rules 1-9, "never claim device behaviour"
  docs/                         existing 15 docs + STATUS.md (new) + RUNNER-ACTIONS.md (new)
  schemas/integrations/         ratified schemas (exists)
  fixtures/                     exists; + sample-repo/ (WP2)
  src/main/kotlin/dev/assay/    JVM engine (exists); + ai/, audit/, prove/ packages
  android-console/              phone app (exists; rewritten in Compose in WP6)
  actions/                      NEW: reusable GitHub Actions workflows (WP2)
    audit.yml                   full 4-scanner audit → ratified tree → push
    prove.yml                   isolated proving-test execution → attempts JSON
    approve.yml                 human-actor candidate approve/reject/apply
  ops/                          existing systemd/tmpfiles for a self-hosted Linux runner
  packaging/linux/              NEW (WP7): install.sh + tarball for the CLI
```

### 3.2 The runner you can use today: **GitHub Actions in the scanned repo**

`RUNNER-DECISION.md` sanctioned "a one-shot Kotlin/JVM process on an isolated Linux host". The Dell homelab is unverified and its role is still open (OQ-33). A hosted `ubuntu-latest` job **is** that one-shot isolated Linux process, with ephemeral workspace, no shell rc, dropped credentials after the job, and docker available for the MobSF container. `scanner-contracts.yml` already proves the three CLI scanners run there. So:

- **Runner v1 = reusable workflows in `actions/`** that any repo calls with `uses: mbaliga/assay/actions/audit.yml@v1`. The scanned repo adds a 12-line caller workflow (Assay generates it: `assay init-actions`).
- **Runner v2 = the same engine on a self-hosted Linux box** via `packaging/linux/install.sh` and the existing `ops/` units (WP7). Same CLI, same artifacts. The Dell, a laptop, or Termux-proot can all be v2 hosts later; nothing in v1 assumes them.
- Network discipline in the job: scanners run in a step with `--offline` where supported and with egress limited to the tool-download step; MobSF runs via `docker run --network none` after the image is pulled by digest in a prior step. Write this down in `docs/RUNNER-ACTIONS.md` and keep `RUNNER-DECISION.md` as the portable decision (add a §"Hosted CI as a conforming runner").

### 3.3 One end-to-end command: `assay audit`

New `AuditCli` (`src/main/kotlin/dev/assay/audit/`):

```
assay audit --repo <path> --commit <sha> --output-branch <name> --remote <url>
            [--scanners gitleaks,semgrep,osv,mobsf] [--apk <path>] [--semgrep-config <path>]
            [--online] [--ai asom|cloud:<provider>|off] [--push]
```

Steps, all existing primitives: `runner-preflight` → for each scanner `ScannerOrchestrator.execute` (MobSF via `MobSfApiClient` when `--apk` given) → `ScanPipeline.bundle` (fail-closed) → `BusWriter.publish(bundle)` (internal bus, finally with real data — delete the `fixture-1` artifact wrapper path for non-fixture runs) → `Sarif.merge` → `AssayOutputWriterV1` → optional AI pass (§3.5) → `GitBusPublisher.publishV1` (ratified tree, two-commit `assayCommit` trick already implemented) and `GitBusPublisher.publish` (internal bus, default `assay/audit`). Exit codes: 0 complete, 2 partial (a scanner failed; `completeness=PARTIAL` + `explanation`), 3 failed. `--push` off by default; the Actions workflow passes it.

`scan-fixture` / `DeterministicSecretScanner` stay for tests but are renamed `fixture-scan` and rejected by `audit`.

### 3.4 Proving tests: generated by AI, executed in isolation, judged by `ProofGate`

- `prove/ProvingTestGenerator` asks the AI adapter for a test *source file* targeting one finding (`testKind` from the ratified enum; default `REGRESSION`, `EXPLOIT_POC` only for Semgrep injection-class rules and only as a minimal repro). Written to `tests/proving/<provingId>.<ext>` under the candidate's fix branch. The `provingId` format is the existing `pt1-<scanner>-<rule-slug>-<fp16>`.
- `actions/prove.yml` checks out the candidate's **base** commit, runs the single test twice (expect fail), checks out the fix commit, runs twice (expect pass), captures exit codes, result digests, and re-runs the originating scanner on both, and emits `attempts.json` in the exact shape `candidate-proof --proof` already consumes. Network is disabled during test execution (`--network none` via a docker step, or `unshare -n` fallback). The test runner command is per-target-type: Gradle (`./gradlew :<module>:testDebugUnitTest --tests <class>`), plain JVM, or Python/Node for non-Android repos; detect from the repo, store the detection in the candidate ledger.
- The Kotlin core never runs these commands itself (rule 4). `candidate-proof` ingests `attempts.json` → `ProofGate` → `proof_passed` or `proof_failed`.

### 3.5 AI adapter (`src/main/kotlin/dev/assay/ai/`)

- `ModelProvider` interface with one method `complete(system, user, params): Completion` returning text + `provenance: LOCAL | CLOUD(providerId)`. Two implementations: `OpenAiCompatProvider` (base URL + optional key; ASOM preset `http://127.0.0.1:11435/v1`, DeepSeek/Grok/OpenAI presets) and `AnthropicProvider` (messages API). Gemini via its OpenAI-compatible endpoint. No SDKs — plain `java.net.http.HttpClient`; no vendor library.
- Keys come from the environment (`ASSAY_MODEL_KEY`) on the runner or from the console's Keystore; never from the repo, never logged, sent only to their own provider's host (assert host == provider base host).
- Tasks the adapter may perform, each a typed request/response with the finding **as input**: `triage` (severity confirmation + exploitability note; cannot lower the pinned SEV floor in `Severity.kt`), `explain` (plain-language explanation stored in `runs/<id>/evidence/<fp>.explanation.md`), `proposePatch` (unified diff applied to `assay/fix/<id>` via existing `CandidateCli` — `candidate-create --actor system:assay-ai`), `generateProvingTest` (§3.4).
- Every AI artifact carries `provenance` and `model` in its JSON and in the evidence file header. `AsomExplanationProvider` (interface only today) is replaced by this.
- If no provider is configured, `--ai off` is implied and the run is still `COMPLETE`. AI is never on the critical path of a finding.

### 3.6 Candidate store persistence for the hosted runner

The hosted job has no disk between runs. Persist the candidate store (`CandidatePersistence`) inside the internal bus branch under `candidates/` (tar + sha256 manifest), restored at job start by `candidate-restore --from-branch`. The ledger's hash chain and lease checks already protect it. Document as contract 1.2.0 (additive) in `CONTRACT.md`.

### 3.7 Console = control plane + offline quick check

Keep the three existing paths (Scan an APK locally; Open verified audit snapshot; Explore sample) and add the control plane:

- **Runs**: list of target repos (user-added: owner/repo + output branch), per repo: last run status, "Run audit now" (GitHub `workflow_dispatch` on the caller workflow), "Refresh" (reads the ratified `assay-index.v1.json` from the output branch + Actions run status). Both are explicit taps — no background polling (same INT-001 discipline as Core).
- **Candidates**: for the selected repo, the candidate list from the internal bus (`console-snapshot` output now published as `console.json` next to `index.json`); detail shows diff summary, proving-test status, scanner before/after. **Approve / Reject** buttons dispatch `approve.yml` with `candidate_id`, `decision`, and `actor=human:<github-login>` resolved from the PAT (`GET /user`). "Apply" dispatches the apply step (merges fix branch into a PR, never auto-merges).
- **Findings never get a browser.** The console shows counts and the Core/Fonebrew hand-off ("Open in Fonebrew Audit: Check branch `<name>`"). Remove `consoleFindingCard` lists; keep candidates (they are control-plane objects).
- INTERNET permission added, used only by the two explicit actions above. Keep `allowBackup=false`, cleartext off. PAT in `EncryptedSharedPreferences`/Keystore.
- Rewrite in **Jetpack Compose + Material3** with Hyle tokens (§6). Views-based `AssayHomeActivity` is replaced; `LocalApkAudit.kt` and `ConsoleSnapshot.kt` are kept verbatim (they're tested logic).

### 3.8 Toolchain lockstep

Move the console to **AGP 8.9.1 / Gradle 8.14.3 / Kotlin 2.1.20 / compileSdk 36 / targetSdk 36 / minSdk 31** (constellation lockstep with Shared-Libraries-asoc and Core; the owner ruled Kotlin 2.1.20 "staged pin"). Add `shared-libraries` as a submodule (`https://github.com/mbaliga/Shared-Libraries-asoc.git`, pin to its current HEAD) and `includeBuild` it **only for `dev.aarso:crash-recovery`** (`CrashRecovery.install(app, "Assay")`). The JVM engine stays on Gradle 8.10.2/Kotlin 2.1.20 as today. CI needs `secrets.CORE_SUBMODULE_TOKEN` if the submodule repo is private — record as `OWNER-ACTION-NEEDED` if checkout fails; do not work around it by vendoring.

---

## 4. Work packages (execute in order; each ends at a gate)

Each WP: (a) read the named files first, (b) implement, (c) run the gate commands unpiped with `LANG=C.UTF-8`, (d) append a dated row to `docs/STATUS.md` with the ladder level and evidence (command + exit code + artifact path), (e) commit on `trunk` with a conventional message, (f) stop and report. Never start the next WP in the same session.

### WP0 — One trunk (effort: xhigh)

Goal: a single `trunk` branch containing all real work, CI green, PR open to `main`.

1. `git switch -c trunk origin/claude/fonebrew-development-clzu43` (newest code: ratified contract, dead `MainActivity` removed, setup-android v4 fix).
2. Merge `origin/feat/fonebrew-execution-contract`. Expected conflicts: `.github/workflows/android-console.yml` → keep trunk's `setup-android@v4` + `packages: platform-tools`; `build.gradle.kts` → keep all acceptance tasks and register `fonebrewExecutionAcceptanceTest` into `check`. Then add a §"Execution is not proof" cross-link in `docs/CONTRACT.md` to `docs/FONEBREW-EXECUTION.md`.
3. Fix the broken heading in `docs/INFORMATION-ARCHITECTURE.md` (`#**Interaction-mode decision…` and the demoted `# Mobile v2 IA`).
4. `git merge --allow-unrelated-histories origin/main` (brings `cleanup-artifacts.yml`; resolve `LICENSE` add/add by keeping Apache-2.0 per §2.3).
5. Merge `origin/claude/multi-platform-porting-plan-nnvjug` (clean). Update its header line "read from PR #1" → "lives on trunk".
6. Cherry-pick only `58ede9d` (`docs/PRODUCT-AND-ARCHITECTURE.md`) from `origin/claude/repo-status-overview-2c0rgm`; fix its "thirteen docs" count. **Do not merge the rest of that branch** (Hyle submodule, AGP downgrade, edits to a deleted file).
7. Delete nothing remote. Leave `apk-testbuild`, `agent/*`, `claude/github-storage-cleanup-*` alone; list them in `docs/STATUS.md` as "superseded, safe to delete (owner)".
8. Extend `ci.yml` triggers to `trunk`. Add `CLAUDE.md` (≤40 lines: gate commands, rules 1–9 in one line each, the environment-honesty rule).
9. Create `docs/STATUS.md` with the ladder legend and one row per WP.

**Gate:** `gradle check --no-daemon` green (all 11+ acceptance harnesses); `android-console` `assembleDebug lintDebug` green; `git log --oneline trunk | wc -l` ≥ 110; PR `trunk → main` opened with the branch map in its description. Ladder: `code-complete`.

### WP1 — `assay audit` end-to-end on the fixture (effort: high)

Read: `Main.kt`, `Scanner.kt`, `Pipeline.kt`, `Bus.kt`, `GitBusPublisher.kt`, `AssayOutputWriterV1.kt`, `MobSfExecution.kt`, `AcceptanceTest.kt`.

1. Implement `AuditCli` per §3.3. Replace the `fixture-1` artifact wrapping in `BusWriter` with the real bundle's scanner artifacts for non-fixture runs. Wire `ScanPipeline.mobSfOutcome` into the bundle when `--apk` is given.
2. Add `AuditAcceptanceTest` (same `main()`-harness style): runs `audit` against `fixtures/` with fake tool binaries (as `RunnerPreflightAcceptanceTest` does), asserts both trees are written, `index.json` and `assay-index.v1.json` validate structurally, every SARIF result carries `scannerName`/`ruleId`, `completeness` logic for a failing scanner, exit codes 0/2/3.
3. Add a real JSON-Schema validation step: vendor **`networknt/json-schema-validator`** (Apache-2.0) to validate `assay-index.v1.json` and `proving-tests.v1.json` against `schemas/integrations/*` at write time; keep the byte-identical mirror test as well.
4. Update `README.md` "Quick start" to the single command.

**Gate:** `gradle check` green incl. the new harness; `scanner-contracts.yml` extended to run `assay audit --scanners gitleaks,semgrep,osv` against `fixtures/` on CI and upload the `.assay/` tree as an artifact (`actions: write` already exists; keep ≤3 artifacts per name). Ladder: `verified` (CI run URL in STATUS.md).

### WP2 — Hosted runner: reusable workflows + sample target (effort: high)

Read: `.github/workflows/scanner-contracts.yml`, `docs/RUNNER-DECISION.md`, `docs/RUNNER-OPERATIONS.md`, `docs/THREAT-MODEL.md`, `docs/TOOLCHAIN.md`.

1. Create `fixtures/sample-repo/`: a minimal Android app (one module, one Activity, deliberately: a hardcoded API key, a `WebView.setJavaScriptEnabled(true)` + `addJavascriptInterface`, an old vulnerable dependency pinned in `libs.versions.toml`, `android:debuggable="true"`). Commit it as a subtree the owner can push to `mbaliga/assay-sample-target` (document the command; do not push it yourself).
2. `actions/audit.yml` (reusable, `workflow_call`): inputs `source_ref`, `output_branch`, `scanners`, `build_apk` (bool → runs `./gradlew assembleDebug` of the target in its own step, then MobSF), `ai` (off|asom|cloud); secrets `ASSAY_MODEL_KEY` (optional). Steps: checkout target at `source_ref` → download the Assay CLI release tarball by SHA-256 (built in WP7; until then `installDist` from a pinned `mbaliga/assay` ref) → download scanners by the pins in `TOOLCHAIN.md` → MobSF: `docker pull opensecurity/mobile-security-framework-mobsf@sha256:<digest>` then `docker run --network none …` using `print-mobsf-container-command` → `assay audit … --push` with `GITHUB_TOKEN` scoped to the target → upload `.assay/` as artifact. No step may run after a failed scanner except the artifact upload.
3. `assay init-actions --repo <path>` writes `.github/workflows/assay.yml` (caller with `workflow_dispatch` inputs mirroring the above) into a target repo.
4. `docs/RUNNER-ACTIONS.md`: the conformance argument vs `RUNNER-DECISION.md` (one-shot, isolated, ephemeral credentials, network discipline per step), plus the "what the hosted runner cannot guarantee" list (no control of the host kernel; MobSF image trust rests on the digest pin).
5. Dry-run the reusable workflow against `fixtures/sample-repo/` inside this repo's CI (`.github/workflows/sample-e2e.yml` calling `actions/audit.yml` locally) with `build_apk: true`.

**Gate:** `sample-e2e.yml` green on CI producing a pushed `.assay/` tree on a branch of this repo (e.g. `assay/sample-output`) with ≥3 findings from ≥3 distinct scanners and a MobSF result with `scannerName=MobSF`. Ladder: `verified`.

### WP3 — Console control plane, part 1: GitHub connection + runs (effort: high)

Read: `android-console/**`, `docs/ANDROID-CONSOLE.md`, `docs/INFORMATION-ARCHITECTURE.md` (answer its seven questions for every new surface, in the doc), `docs/USER-JOURNEYS.md`.

1. Toolchain lockstep per §3.8; crash-recovery adopted. Compose + Material3 scaffold with `HyleTokens.kt` (§6); navigation: Home / Repos / Runs / Candidates / Settings.
2. `GitHubClient` (plain OkHttp or `HttpURLConnection`, no SDK): `GET /user`, `POST /repos/{o}/{r}/actions/workflows/assay.yml/dispatches`, `GET …/actions/runs`, `GET /repos/{o}/{r}/contents/.assay/assay-index.v1.json?ref=<branch>` (+ `console.json`). PAT in Keystore. Every call originates from a tap; add `NetworkPolicyTest` that fails if any network call is reachable from a lifecycle callback or a `WorkManager` worker.
3. Repos screen: add/remove target (owner/repo, output branch, "has caller workflow?" check with a one-tap "generate caller workflow → opens a PR" using the API `PUT contents`).
4. Runs screen: dispatch, refresh, status chips (queued/running/complete/partial/failed — glyph + label), link to the run, and the Fonebrew hand-off line.
5. Keep "Scan an APK" and "Open verified audit" paths; replace the finding list in the snapshot view with counts + candidate list only.

**Gate:** `assembleDebug lintDebug` green; Robolectric tests for `GitHubClient` with MockWebServer (dispatch body, headers, pagination, 401 handling); `NetworkPolicyTest` green; screenshot tests via Roborazzi for the five screens in light/dark and 200 % font scale. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION` rows for: install on RedMagic, PAT entry, one real dispatch.

### WP4 — AI layer + generated proving tests + isolated proof execution (effort: xhigh)

Read: `Proof.kt`, `Model.kt`, `Candidate.kt`, `CandidateCli.kt`, `CandidatePersistence.kt`, `IntegrationProjection.kt`, `docs/CANDIDATE-LIFECYCLE.md`, `docs/FONEBREW-EXECUTION.md`, `docs/V2-ROADMAP.md`.

1. `ai/` package per §3.5 with `OpenAiCompatProvider`, `AnthropicProvider`, prompts as resources under `src/main/resources/prompts/` (versioned; the prompt version is recorded in every AI artifact). Unit tests with a fake HTTP server for each provider; `AiCannotOriginateFindingsTest` (rule 1).
2. `assay triage --run <id>` / `assay explain` / `assay propose --finding <fp>`: the last creates a candidate via `CandidateCli` with `--actor system:assay-ai`, applies the diff on `assay/fix/<id>`, generates the proving test into `tests/proving/`, commits, and records `provingTestRef` in the SARIF result properties (already supported by `Sarif.kt`).
3. `prove/` + `actions/prove.yml` per §3.4; `attempts.json` emitter in the workflow (a small shell+jq or a `assay prove-record` subcommand that only *records* exit codes handed to it). `candidate-proof` unchanged.
4. Candidate-store persistence on the internal bus branch (§3.6) with `candidate-persist`/`candidate-restore` subcommands and an acceptance harness proving round-trip + tamper detection.
5. `actions/approve.yml`: inputs `candidate_id`, `decision` (approve|reject|apply), `actor` (validated `^human:[A-Za-z0-9-]+$`); runs `candidate-restore` → `candidate-approve|reject|apply` → `candidate-persist`. `apply` opens a PR from `assay/fix/<id>`; never merges.
6. `proving-tests.v1.json` is now generated from real candidates and real attempt results (remove the pipe-delimited CLI string input from `publish-output-git`; keep it only under `--legacy`).

**Gate:** `gradle check` green; `sample-e2e.yml` extended: `ai: asom` is not available on CI, so run with a **fake provider** (`ASSAY_FAKE_MODEL=1` returns canned diff + test from fixtures) through propose → prove (fail×2 / pass×2 on the sample repo's hardcoded-key finding) → `candidate-proof` → `proof_passed` → `approve.yml` with `human:ci-fixture` → PR opened. Ladder: `verified` for the pipeline; the real-model path is `code-complete` + `OWNER-ACTION-NEEDED` (key / ASOM on the runner).

### WP5 — Console control plane, part 2: candidates, approve/reject, AI provider settings (effort: high)

1. Candidates screen from `console.json` (internal projection): diff summary, scanner before/after, proving status with the exact fail/pass counts, explanation (AI, with provenance glyph), "Approve", "Reject", "Apply" → `approve.yml` dispatch with `actor=human:<login>`. Reject requires a reason (stored in the ledger event).
2. Settings: GitHub PAT; AI provider for the *runner* is a repo secret (explain, link); AI provider for *on-phone explanations* (optional): ASOM at `127.0.0.1:11435` (local glow) or a cloud key (cloud glow, watched-object badge on every screen that shows AI text).
3. Replace "copy approval command" with the real action; keep it as a fallback under a "Manual" disclosure.

**Gate:** as WP3 plus Roborazzi for the candidate screens and a Robolectric test asserting the Approve action cannot be dispatched without a resolved `human:` login. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`.

### WP6 — Production hardening + release lane (effort: high)

1. Accessibility pass: TalkBack content descriptions for every control, 200 % text, both themes; no colour-only state (automated check: a lint rule or a test that scans the status-chip composables for the `shape+label` pair).
2. Threat-model review doc update (`THREAT-MODEL.md`): hosted runner, PAT scope, AI provider egress, candidate-store-on-branch.
3. Console release build: `minifyEnabled`, `signingConfig` from env vars (never committed); versionName `1.2.0`. `android-console.yml` uploads a debug APK on every trunk push and a release APK on tags when secrets exist.
4. The 12 V2-ROADMAP certification items become a checklist in `docs/VERIFICATION.md` with the hosted-runner equivalents filled in with run URLs where CI covered them, and `NEEDS-DEVICE-VALIDATION` / `OWNER-ACTION-NEEDED` where not.
5. `README.md`: 10-minute quick start (add caller workflow → tap Run on phone → Check branch in Fonebrew Audit).

**Gate:** all CI green on trunk; `docs/VERIFICATION.md` has no empty cells. Ladder: `ready-for-owner-review`.

### WP7 — Linux CLI release lane (effort: medium)

`packaging/linux/`: `install.sh` (verifies SHA-256), `jlink` image or tarball, draft GitHub Release on tag. `actions/audit.yml` switches from `installDist` to the release tarball by digest. Verify on CI by installing from the artifact and running `assay audit` on the fixture. Ladder: `verified`.

---

## 5. Definition of "production-ready today" (what the owner should be able to do after WP6)

1. In any Android repo: `assay init-actions .` → commit → push.
2. On the phone: add the repo, tap **Run audit**, see it go queued → running → complete.
3. In Fonebrew Audit: pick the repo + output branch, **Check branch**, see findings with `scannerName`/`ruleId`/`provingTestRef`.
4. On the phone: open a candidate, read the AI explanation (with provenance), see "proof: failed 2/2 before, passed 2/2 after", tap **Approve**, tap **Apply**, get a PR.
5. Everything above with the cloud provider unset — ASOM or no AI — and the audit still completes.

---

## 6. Design tokens (copy into `android-console/.../HyleTokens.kt`; do not import the Hyle submodule)

- Ground `#07070A` (never pure black except for pixels-off states). Violet `#8E7BFF` (brand/primary). Radium `#CCEC75` (on-device/local provenance glow). Alien `#3ED4FF` (cloud provenance glow). Cyan `#08FED5` is retired — do not use.
- Severity encoding: SEV1 ◆ filled diamond, SEV2 ◇ outlined diamond, SEV3 ▲, SEV4 ○ — always with the label text; luminance steps 100/80/60/40 % of on-surface. Never red/green.
- Candidate states: `proposed` dashed outline, `proof_passed` solid outline + check glyph, `approved` filled, `rejected` strikethrough + ✕, `stale` dimmed + clock glyph.
- Minimalist, generous whitespace, no boxy card grids; explanatory copy lives behind an info icon; no decorative connector lines. Dark theme is primary; light must still pass contrast.

---

## 7. Reporting format at the end of every WP

```
WP<n> — <title>
Ladder: code-complete | verified | ready-for-owner-review
Gate commands (unpiped, LANG=C.UTF-8): <cmd> → exit <code> … 
Evidence: <CI run URL | artifact path | test names>
NEEDS-DEVICE-VALIDATION: <bullets or "none">
OWNER-ACTION-NEEDED: <bullets or "none">
Not done / deferred: <bullets>
Next: WP<n+1> (do not start)
```
