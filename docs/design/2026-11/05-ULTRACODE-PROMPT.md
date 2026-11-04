# Assay — build prompt for a Sonnet Claude Code session

Paste everything below the line into a fresh Claude Code session with `mbaliga/assay` attached. Session settings: **model `sonnet`**, **`/effort high`** (`xhigh` for WP-A4 and WP-A5). One work package per session. Run `/code-review` before reporting a WP.

---

Read, in this order, before touching anything: `CLAUDE.md`, `BUILD-SPEC.md`, `docs/STATUS.md`, then the four-lens design folder `docs/design/2026-11/` — `00-REVIEW.md`, `03-ARCHETYPES-AND-JOURNEYS.md`, `04-ROOMS-UX.md`, `02-ON-DEVICE-INTELLIGENCE.md` (+ `models-catalogue.v1.json`), `01-COMPETITIVE-LANDSCAPE.md`. The design folder re-sequences BUILD-SPEC's WP1–WP7 into the packages below. Where the two disagree on *sequence or UI*, the design folder wins. Where they disagree on *rules*, BUILD-SPEC §1 and `CLAUDE.md` win, always.

Start from the branch `design/four-lens-review` (it is the Nov 3 trunk plus the design folder): `git switch -c trunk origin/design/four-lens-review`. Commit on `trunk`. Open one PR `trunk → main` at the end of WP-A0 and keep pushing to it.

## Rules you are bound by (restated; the full set is BUILD-SPEC §1)

1. Only the four scanners may construct a `Finding`. The AI adapter's return types cannot carry one. `AiCannotOriginateFindingsTest` greps for it.
2. `ProofGate` is the only arbiter of `proof_passed`; proof is exit codes.
3. Approve/reject requires a `human:` actor resolved from the GitHub login; the console's Approve control is a long-press, never a modal "OK".
4. The Kotlin core never runs the scanned repo's build in-process.
5. No telemetry. Network only for pinned tool/model downloads, the user's git/GitHub, ASOM, or a provider the user configured — and every call originates from a tap or a pull on a named control (`NetworkPolicyTest`).
6. Permissive licences only for linked libraries. llama.cpp (MIT) is fine; no vendor AI SDKs.
7. Colour never carries state alone; marks are glyph + label with the luminance ladder; red/green never.
8. Anti-overclaim ladder: `code-complete` | `verified` | `ready-for-owner-review`. Device behaviour is `NEEDS-DEVICE-VALIDATION`. Never "done", "works on device", "production-ready".
9. Environment honesty: stub loudly with typed errors; never fake success. `DemoData.kt` stays and says SAMPLE.
10. Two contracts (`.assay/` ratified vs internal bus 1.1.0); never merge them.
11. Do not rename the app, package, or branches; do not touch `LICENSE`; do not add the Hyle submodule (copy token values into `HyleTokens.kt`).
12. Findings never get a browser in the console. Counts + "Open in Fonebrew Audit" only.
13. Copy: explanations behind info icons; no "AI-sounding" copy; the state words are `proposed · proof passed · approved · rejected · stale · applied`.

## Work packages

Each WP: read the named files; implement; run the gates unpiped with `LANG=C.UTF-8`; append a dated row to `docs/STATUS.md`; commit; stop and report in the §7 format of BUILD-SPEC. Never start the next WP in the same session.

### WP-A0 — Trunk, CI, PR (effort: medium)
1. Extend `.github/workflows/android-console.yml` triggers to `trunk`, `ccr-*`, `design/*`; keep `ci.yml` as is.
2. Open PR `trunk → main` with the branch map from `docs/design/2026-11/00-REVIEW.md` §1 in the description.
3. Record in `docs/STATUS.md` which superseded branches the owner can delete (already listed; keep).
**Gate:** both workflows green on `trunk` on CI (run URLs in STATUS.md). Ladder: `verified`.

### WP-A1 — `assay audit` end-to-end (effort: high)
Exactly BUILD-SPEC WP1. Read `Main.kt`, `Scanner.kt`, `Pipeline.kt`, `Bus.kt`, `GitBusPublisher.kt`, `AssayOutputWriterV1.kt`, `MobSfExecution.kt`, `AcceptanceTest.kt`. Deliver `AuditCli`, remove the `fixture-1` wrapping for non-fixture runs, `AuditAcceptanceTest`, networknt schema validation at write time, README quick start. Plus three **P0** items from `01-COMPETITIVE-LANDSCAPE.md` §5 that belong here: CWE taxonomy tags on every SARIF result where the scanner provides them; the scanned commit/APK digest stamped into the ratified index; an EPSS score on OSV results from an offline EPSS CSV pinned by hash in `tool-lock.json` (additive to the ratified schema; bump its minor version and mirror the change in `schemas/integrations/`).
**Gate:** `gradle check` green incl. the new harness; `scanner-contracts.yml` runs `assay audit` on CI and uploads the `.assay/` tree. Ladder: `verified`.

### WP-A2 — Hosted runner: reusable workflows + sample target (effort: high)
Exactly BUILD-SPEC WP2: `fixtures/sample-repo/`, `actions/audit.yml`, `assay init-actions`, `docs/RUNNER-ACTIONS.md`, `sample-e2e.yml`.
**Gate:** `sample-e2e.yml` green with ≥3 findings from ≥3 scanners and a MobSF result. Ladder: `verified`.

### WP-A3 — Console on the room grammar: shell, on-ramp, GitHub, Runs (effort: high)
Read `android-console/**`, `docs/ANDROID-CONSOLE.md`, `docs/design/2026-11/04-ROOMS-UX.md` §1–§4 and §9, `03-ARCHETYPES-AND-JOURNEYS.md` J1–J3.
1. Toolchain lockstep per BUILD-SPEC §3.8; crash-recovery via the `shared-libraries` submodule (`includeBuild` only for `dev.aarso:crash-recovery`; if the submodule checkout fails on CI, record `OWNER-ACTION-NEEDED: CORE_SUBMODULE_TOKEN` and keep going without it).
2. Rewrite the console in Compose + Material3 with `HyleTokens.kt` (BUILD-SPEC §6). Keep `LocalApkAudit.kt` and `ConsoleSnapshot.kt` verbatim.
3. Build the **room shell** as a reusable module (`android-console/rooms/`): an origin room, four cardinal rooms by horizontal/vertical pager with velocity-gated swipes, a compass with tap parity, Z axis (pinch-out overview, pinch-in detail, `+/−`, zoom-to-fit), immersive/regular modes with parity, predictive back returning to origin, process-death restoration of room + Z + scroll + open sheet, RTL mirroring, TalkBack custom actions for every move, hardware keys. No gesture without a non-gesture equivalent.
4. Rooms per `04-ROOMS-UX.md` §2–§3: **Bench** (origin: needs-you / running / recent; pocket actions for APK quick check, import snapshot, sample), **Repos** (west), **Runs** (north), **Candidates** (east, list only in this WP — the proof view is WP-A6), **Settings** (south, cells; Models cell is a placeholder until WP-A4), **Portfolio** (Z-out).
5. **First launch** per J1: cell-growth loader (≤900 ms, skippable, reduced-motion fade), the four-cell diagram with one info icon per cell, the one-question Guidance dial (Guide / Work / Expert), entry pick, compass overlay shown once *after* a first result exists. Deep-link entry (`assay://candidate/<id>`, `assay://run/<id>`) skips the on-ramp and asks the dial question after the result.
6. `GitHubClient` (OkHttp, no SDK): `GET /user`, workflow dispatch, runs list, contents read of `assay-index.v1.json` and `console.json`. PAT in Keystore via `EncryptedSharedPreferences`/Keystore AES; scope verified; admin-scoped tokens refused with a one-line reason. One-tap "add the caller workflow" opens a PR via `PUT contents`.
7. States per the §9 screen inventory rows 1–5, 9, 11–13. Empty Bench is the three pocket actions as cells; never an empty-inbox illustration.
8. Marks per `04-ROOMS-UX.md` §6 as composed cell shapes (a small `Marks.kt` that draws plant-cell geometry from a seed; straight-ish walls, rounded junctions); every mark carries its label in the same semantics node.
9. Extend `LocalApkAudit.kt` (**P0**, `01-COMPETITIVE-LANDSCAPE.md` §5): deep-link filters, `addJavascriptInterface` / `setJavaScriptEnabled` detection in dex strings, `networkSecurityConfig` parse (cleartext, user CAs), `debuggable`, backup flags — each as a labelled check with the "Local quick check" trust label unchanged. Add fixtures and unit tests per check.
**Gate:** `assembleDebug lintDebug` green on CI; Robolectric tests for `GitHubClient` (MockWebServer: dispatch body, headers, pagination, 401), `NetworkPolicyTest`, `RoomShellTest` (back returns to origin; restoration; compass parity), `MarksTest` (every mark has a label); Roborazzi screenshots of Bench, Repos, Runs, Settings, First launch in light/dark and 200 % font. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION` rows: install on RedMagic, PAT entry, one real dispatch, gesture feel.

### WP-A4 — Models room and the phone-side model layer (effort: xhigh)
Read `docs/design/2026-11/02-ON-DEVICE-INTELLIGENCE.md` fully, `04-ROOMS-UX.md` §5.
1. `ModelProvider` interface (shared shape with the runner adapter in WP-A5): `complete(system, user, params): Completion` with `provenance`, `model`, `promptVersion`, optional `confidence`. Implementations: `AsomProvider` (OpenAI-compatible at `127.0.0.1:11435`, AIDL pairing check), `LlamaCppProvider` (embedded llama.cpp via JNI, built from a pinned llama.cpp commit with CMake in the Gradle build; GBNF grammar support; per-token logprobs), `OpenAiCompatProvider`, `AnthropicProvider`, and `AiCoreProvider` behind a capability check (stub with a typed `Unavailable` error where the API is absent).
2. The catalogue: ship `assets/models/catalogue.v1.json` from `docs/design/2026-11/models-catalogue.v1.json` with every `PIN-AT-BUILD` field (revision, file, bytes, sha256) resolved from `GET /api/models/{repo}?blobs=true` by a Gradle task that fails the build on a missing pin; its JSON schema; an Ed25519 signature check for refreshed copies (key in the APK; the refresh URL is a pinned GitHub raw URL). The `LlamaCppProvider` build must use a post-Mar-2026 llama.cpp commit so Qwen3.5's hybrid architecture loads.
3. Downloader per §5: `Range` resume, streamed SHA-256, free-space precheck, Wi-Fi/charging conditions, foreground service; mismatch deletes and shows digests.
4. Bring-your-own per §6: URL and SAF file; GGUF header/metadata parser (`general.architecture`, name, context length, quant, tokenizer); `.litertlm`/`.task` manifest read; architecture support check; fit mark; detected card; nothing executes before Install.
5. The **Models room** per `04-ROOMS-UX.md` §5: three shelves (On this phone / Catalogue / Bring your own), cloud BYOK providers as watched objects (alien glow, "sent only to `<host>`", host assertion at call time), ASOM as a local provider when paired, per-task defaults (`explain`, `digest`, `translate`), load/unload with the radium glow, the fit mark from measured RAM, first-load timing shown.
6. Phone-side tasks `explain`, `digest`, `translate` per §7 with versioned prompts under `src/main/resources/prompts/` and GBNF grammars; provenance badge wherever AI text appears.
7. `AiEvalTest` against a fake provider for every task; `AiCannotOriginateFindingsTest`; a `ModelFileInspectorTest` with truncated/unsupported/valid GGUF fixtures.
**Gate:** `gradle check` and console build green; the tests above; Roborazzi for the Models room shelves, download in progress, gated prompt, BYOM card, BYOK provider. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`: one real download, one BYOM install from a file, one on-device `explain` on the RedMagic with timing recorded in `docs/design/2026-11/model-eval.md`.

### WP-A5 — AI on the runner, generated proving tests, isolated proof, approve lane (effort: xhigh)
Exactly BUILD-SPEC WP4, reusing the `ModelProvider` shape from WP-A4: `ai/` package on the JVM side, `assay triage|explain|propose`, `prove/` + `actions/prove.yml`, candidate-store persistence on the bus branch, `actions/approve.yml`, real `proving-tests.v1.json`.
**Gate:** `sample-e2e.yml` extended with the fake provider through propose → prove → `proof_passed` → `approve.yml` with `human:ci-fixture` → PR opened. Ladder: `verified`; the real-model path `code-complete` + `OWNER-ACTION-NEEDED`.

### WP-A6 — Candidates room, proof view, decisions (effort: high)
Read `04-ROOMS-UX.md` §3 Candidates, §4; `03-ARCHETYPES-AND-JOURNEYS.md` J3–J5, J7.
1. Candidates room tabs Open / Decided / Stale; the ladder glyph per candidate.
2. **Proof view** at three densities (Guide: ladder picture + explanation on top; Work: + scanner before/after; Expert: diff on top, digests block). Approve = long-press with a growing cell ring (≥600 ms); Reject = tap → reason sheet, empty refused; Apply → PR link replaces the control. Decisions tab filtered by `human:` actor.
3. Stale handling, failure states per J5 (never a disappearing toast), the read-only Snapshot room (no Approve control rendered), the Fonebrew/Orrery share-sheet hand-offs.
**Gate:** Robolectric test that Approve cannot dispatch without a resolved `human:` login; Roborazzi for the proof view at all three densities, reject sheet, applied, stale, snapshot read-only. Ladder: `code-complete` + `NEEDS-DEVICE-VALIDATION`.

### WP-A7 — Credibility, hardening, release lane (effort: high)
1. Implement the items marked **P0** in `docs/design/2026-11/01-COMPETITIVE-LANDSCAPE.md` §"What Assay must match" that are not already covered above.
2. Accessibility pass per `04-ROOMS-UX.md` §8; a test that scans every mark composable for the glyph + label pair; 200 % font Roborazzi rows for every room.
3. Earcons and haptics hooks (Hyle preset names; sounds and haptic patterns are assets the owner supplies — stub with typed `NotProvided` and a Settings toggle; never fake).
4. `THREAT-MODEL.md` update (hosted runner, PAT scope, model-file trust, BYOK egress, candidate store on branch).
5. Release build: `minifyEnabled`, signing from env (never committed), versionName `1.2.0`; `android-console.yml` uploads a debug APK on every trunk push and a release APK on tags when secrets exist.
6. `docs/VERIFICATION.md` as a checklist with CI run URLs where covered and `NEEDS-DEVICE-VALIDATION` / `OWNER-ACTION-NEEDED` where not; `README.md` ten-minute quick start (caller workflow → tap Run → Check branch in Fonebrew Audit).
**Gate:** all CI green on `trunk`; `docs/VERIFICATION.md` has no empty cells. Ladder: `ready-for-owner-review`.

### WP-A8 — Linux CLI release lane (effort: medium)
Exactly BUILD-SPEC WP7. Ladder: `verified`.

## Do not

- Do not build a findings browser, a security score, charts of findings, or any dashboard. Counts and the hand-off only.
- Do not poll in the background. Do not add analytics of any kind.
- Do not inline colour literals; add missing tokens to `HyleTokens.kt`.
- Do not write "production-ready". Do not tick a `NEEDS-DEVICE-VALIDATION` row.
- Do not improvise the look of a screen that `04-ROOMS-UX.md` specifies; if a state is missing from its inventory, add the state to the doc in the same commit and say so in the report.

## Report format (end of every WP)

```
WP-A<n> — <title>
Ladder: code-complete | verified | ready-for-owner-review
Gate commands (unpiped, LANG=C.UTF-8): <cmd> → exit <code> …
Evidence: <CI run URL | artifact path | test names>
NEEDS-DEVICE-VALIDATION: <bullets or "none">
OWNER-ACTION-NEEDED: <bullets or "none">
Not done / deferred: <bullets>
Design-doc amendments made: <files or "none">
Next: WP-A<n+1> (do not start)
```
