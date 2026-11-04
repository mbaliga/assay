# Assay — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06). Status: **PLAN — nothing in this document has been built.** Every claim about
> a target platform is labelled with its evidence class (§0). A platform track updates only its own §4 row and records evidence in `docs/VERIFICATION.md` (no PROGRESS/STATE file
> here).
>
> **What was read.** `main` (ab170e2) holds only LICENSE and `.github/workflows/cleanup-artifacts.yml`: no README, no `docs/`. Code, docs and workflows below were read from the repo (this plan now lives on trunk)
> (`agent/assay-v1-foundation`, draft, head fb150ab, read-only worktree). PR #3 (bdd894b, "supersedes #1"), PR #5 (fd9d638, stacked on #1) and PR #2 (58ede9d, Hyle wiring, never
> green) were listed through the GitHub REST API on 2026-10-06; their contents come from the profile reader's notes and were not re-read here. Which PR is canonical is OQ-30 (§8);
> if it is not #1, re-check §2. **README pointer:** none is added here. It belongs in the canonical PR's README "Documentation" list ("Trust, implementation and operations").

## 0. Evidence labels (never dropped)

`LAB` · `CI (hosted VM)` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` · `SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` ·
`SYNTHETIC` · `CI-ONLY / NOT RUN` · `NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV) · `PLAN` (this document) · `NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` ·
`BROWSER-HEADLESS`. Not used by any Assay row today: `LAB`, `EMULATOR EVIDENCE`, `SIMULATED — NOT DEVICE EVIDENCE`, `SYNTHETIC`, `CI-ONLY / NOT RUN`.

**Proposed mapping (not a ruling; it takes effect only if the canonical PR carries it into `docs/VERIFICATION.md`):** `docs/VERIFICATION.md` keeps its own three levels and its rule "No row may be promoted by prose". The program's labels would map onto it as follows and replace nothing:

| VERIFICATION level (existing) | Program labels that may qualify a port row there | Not allowed |
|---|---|---|
| (no row yet) | `PLAN`; a plan is not a level and VERIFICATION.md has no port row today | Calling a planned port "implemented" |
| implemented | `CONTAINER-BUILD-ONLY` (JVM x86_64 compile/tests in the planning container) | Calling it verified |
| mechanically verified | `CI (hosted VM)`, `SIMULATOR`, `BROWSER-HEADLESS`, `CI-APPROX — NOT DEVICE EVIDENCE` (qualifier only) | Reading any of these as device evidence |
| environment-certified | Only a filled certification record (VERIFICATION template) from the named device or host; until then `NDV`/`NOV` and the row reads "pending" | Waydroid on a non-UT desktop is `VIRTUALIZED — NOT DEVICE EVIDENCE` |

A label qualifies the Evidence column and never changes Status. New port rows enter `docs/VERIFICATION.md` in the canonical PR (not here) as `pending` and are promoted only by
named evidence. The ceiling stays `ready-for-human-review`. `NOT-APPLICABLE` is a scope statement, not a status.

## 1. What this repo is, in porting terms

- **Product.** A deterministic security-audit, proof and remediation-control system for Android repos: a pure-JVM Kotlin CLI runs pinned Gitleaks/Semgrep/OSV-Scanner and MobSF
  (pinned container), normalizes to SARIF 2.1.0, publishes an evidence bus (orphan `assay/audit` branch, `--force-with-lease`) and drives a candidate lifecycle with a mechanical
  proof gate and human-only approval. The only end-user surface is a read-only Android console (`android-console/`, `dev.assay`, no Internet permission) (`README.md`).
- **State.** Active, draft PRs only; CI green at fb150ab. No `assay/audit` branch exists on the remote (REST, 2026-10-06). The Dell runner, hosted publication, pinned MobSF on a
  real APK and every physical-device item are `pending` (`docs/VERIFICATION.md`). Targets today: Linux x86_64 on `ubuntu-latest` (`ci.yml`, `scanner-contracts.yml`), a documented
  but uncertified self-hosted Linux runner (`docs/RUNNER-OPERATIONS.md`), and an Android 8.0+ debug-signed APK (`android-console.yml`).
- **Stack.** Engine: Kotlin 2.1.20, JVM 17, JDK standard library only (no third-party library), hand-written `Json.kt`; Gradle 8.10.2 in CI, no wrapper; `check` runs eight
  `main()`-style JavaExec suites, no JUnit. Console: AGP 9.3.0, Gradle 9.5.0 in CI, SDK 36, minSdk 26, plain `android.widget` Views (no Compose), no Kotlin plugin declared.
- **Size** (PR #1 worktree, fb150ab): 82 tracked files (`git ls-files | wc -l`); 35 Kotlin files, 7,048 lines (`git ls-files '*.kt' | xargs cat | wc -l`): engine 4,416, eight
  suites 874, console 1,758; 11,104 text lines overall (`git ls-files | grep -v -E '\.(png|webp|jar|apk)$' | xargs cat | wc -l`).

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `src/main/kotlin/dev/assay/` core (Bus, Candidate*, Proof*, Sarif, Redaction, ContractValidator, Json, MobSf …) | SARIF, fingerprints, redaction, atomic bus, hash-chained candidates, proof gate | pure-kotlin-jvm | 2,427 | No `android.*`. POSIX-shaped atomicity; `commonMain` would need `java.nio` replaced, not worth it |
| same dir, adapters + CLI (Scanner, GitBusPublisher, MobSfExecution, RunnerPreflight, IntegrationProjection, Main …) | Spawns scanners, git, podman; preflight; projections; ~30 commands | pure-kotlin-jvm, **Linux-host-bound by contract** | 1,989 | The production pipeline: a runner concern, not an app. Eight acceptance suites (874 LOC) need real `git` |
| `ConsoleSnapshot.kt`, `DemoData.kt` | Strict `console-snapshot` 1.1.0 validator (exact keys, 8 MB cap, regexes, cross-references) + labelled sample | kmp-common candidate | 231 | Only Android dependency is `org.json` |
| `AssayHomeActivity.kt`, `ViewTextCompat.kt`, `res/` (129 strings) | Three-path home, trust banners, cards, dialogs, copy-command templates | android-bound | 787 | Not Compose: elsewhere it is a rewrite, not a move. `MainActivity.kt` (438) is dead code, deleted in PR #3 |
| `LocalApkAudit.kt` | "Scan an APK" quick check via `PackageManager` | android-bound | 302 | No equivalent off Android; not ported |
| `schemas/` (6), `ops/` + `scripts/`, `.github/workflows/` (3), `docs/` (14) | Contract JSON Schemas; systemd/tmpfiles/preflight; three Linux lanes; docs | neutral · Linux-only · other · other | 363 · 104 · 219 · 2,557 | Schemas are the engine's source of truth. All three workflows upload Actions artifacts (R6 conflict, §8 Q9) |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| `PackageManager.getPackageArchiveInfo`, `getPermissionInfo`, `signingInfo`; SAF `OpenDocument`, `ContentResolver` | `LocalApkAudit.kt:57-120, 225-279`; `AssayHomeActivity.kt:31-36, 142-185` | The whole APK quick check has no equivalent off Android (new AXML and signature parsers would be new code); the picker maps to F6, with the 8 MB and 1 GB bounds re-enforced |
| `org.json` (lenient) vs engine `Json.kt` (strict: rejects duplicate keys and trailing text) | `ConsoleSnapshot.kt:3-4`; `Json.kt:33,64` | The two may disagree on malformed input (§6 L2) |
| `os.name`, `user.name`, cgroup v2 file; `podman run` flags; per-`linux_x64` tool digests; `ProcessBuilder` | `RunnerPreflight.kt:10-15, 44-66`; `MobSfExecution.kt:219-230`; `scanner-contracts.yml` | Linux-only by contract; impossible on iOS or in a confined click; other OS/arch needs its own pinned digests |
| `setPosixFilePermissions` inside `catch (UnsupportedOperationException)`; directory `FileChannel.force(true)` | `CandidatePersistence.kt:268, 292-313`; `Bus.kt:265-270` | On NTFS the owner-only guarantee would be silently lost. The candidate-store directory sync is unguarded (expected to throw on Windows: prediction); the bus one is inside `runCatching`, so durability would silently weaken there |

## 3. Binding rules this port must not break

- **No telemetry.** The only network calls are ones the operator asks for: MobSF on 127.0.0.1, `git push` for publication, OSV `--online` only when passed; Semgrep metrics are off
  (`docs/TOOLCHAIN.md`, `README.md`). Program I-1: a viewer makes no network call and its README says so, since desktop and iOS have no "no-Internet" entitlement.
- **Authority and evidence.** AI is optional and downstream and cannot originate findings, record proof, approve, apply or publish; approve/reject need `human:<identity>` on a
  PROOF_PASSED candidate at the exact revision; APPLIED never means merged (`README.md`, `docs/THREAT-MODEL.md`, `docs/CANDIDATE-LIFECYCLE.md`). Reports are redacted at capture and
  again at publication (`Redaction.kt`, `docs/THREAT-MODEL.md`).
- **Fail-closed readers.** NotConfigured, Unavailable, Invalid and stale are never an empty finding set: "Missing is not clean" (`docs/CONTRACT.md`, `docs/PRODUCT-EXPERIENCE.md`);
  a viewer reproduces every state.
- **The console is read-only.** It cannot run scanners, record proof, approve, reject, apply or merge; copying a command is not a decision; mobile approval is out of scope until a
  dedicated threat-model review (`docs/ANDROID-CONSOLE.md`, `docs/V2-ROADMAP.md` Epic F). No port adds mutation authority. The registry's "P6" wording ("control-plane only, never a
  findings browser") exists only in Personal-Tracker's `CONSTELLATION.md`; the BRIEF.md and CONSTELLATIONHANDOFF.md it quotes are in no commit of PR #1, and the shipped "Open
  verified audit" screen renders read-only findings and candidate lists. Flagged, not resolved (§8 Q3).
- **Pinning and layering.** Executable SHA-256 and reported version are verified before execution and `tool-lock.json` is deployment-supplied (`docs/TOOLCHAIN.md`). The engine
  stays Android-free ("an independent Gradle project so Android dependencies cannot affect the JVM scanner engine", `docs/ANDROID-CONSOLE.md`) and Linux-runner-shaped
  (`docs/RUNNER-OPERATIONS.md`). PR #3 only: Fonebrew Core's ratified ASSAY_REPO_CONTRACT_V1 is the source of truth, "spoke conforms to hub" (`AssayContractV1.kt` header).
- **Trust labels and colour.** "Local quick check", "Runner-verified evidence", "Sample data", "Unavailable or invalid" stay visible before results; Finding not issue, Candidate
  not fix, Proof passed not fixed, Approved not safe; status never by colour alone; hashes selectable and monospace (`docs/PRODUCT-EXPERIENCE.md`,
  `docs/INFORMATION-ARCHITECTURE.md` accessibility section). Program I-3 agrees and the owner is red-green colourblind: the Android palette gives SEV1/SEV2 and rejected/stale a red
  hue and approved/applied a green hue (`AssayHomeActivity.kt:663-683`), each chip with a word; a port does not copy those hue roles unreviewed (OQ-29, §8 Q5).
- **Local APK observations** never enter the runner finding hierarchy, never leave the device, and the temp copy is deleted in `finally` (`docs/ANDROID-CONSOLE.md`,
  `docs/V2-ROADMAP.md` Epic N). **IA governance:** any new surface, including a viewer elsewhere, answers the seven `docs/INFORMATION-ARCHITECTURE.md` questions first.
- **Program rules.** I-4 (nothing is claimed to work where it was not run; Dell and device items stay pending, `docs/VERIFICATION.md`), R1 (no port edit to `src/` or
  `android-console/`), R2 (gates stay green: `gradle check --no-daemon` at root; `gradle -p android-console :app:lintDebug :app:assembleDebug`), R3, R4, R6, R11 (NAMES.md row
  before any identifier; only `dev.assay` exists), R12 (a viewer is a "snapshot viewer", never "the Assay app"). Licence Apache-2.0, no SPDX banners, no third-party library today:
  none is added, and any future dependency gets a recorded licence entry (I-11).

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | Pipeline `NOT-APPLICABLE` (a confined click cannot spawn scanners, run podman, pass preflight or hold a push credential). Console, two shapes: **(a)** the unmodified `assay.apk` under Waydroid (no GMS, no Internet permission, minSdk 26; expected to keep the APK quick check because PackageManager is present under Waydroid — never run, NDV; owner-device evidence, not a port, OQ-21); **(b)** a thin QML + Lomiri.Components "snapshot viewer" click: validate `console.json`, import via content-hub, trust banners, read-only lists, no `networking` group, no APK scan. F7's JVM bridge is not used | OQ-1 (no UT device); OQ-21; R11/OQ-25 (no click name); F1 `Tokens.qml` absent and OQ-29; Qt 5.15 end-of-road; no real snapshot until the canonical PR lands and the bus exists | 3 for (b) incl. click-review template and 26.04 canary; 0 engineering for (a) | PLAN |
| Linux desktop | already-done | The pipeline already runs on `ubuntu-latest` with a documented hardened runner package; what remains is packaging: a CLI release lane (jlink image or tarball + `install.sh`, x86_64, draft GitHub Release, no Flatpak for a CLI) and a `console-core` KMP module (validator + sample + golden fixtures, `jvm()` lane). A Compose Desktop "snapshot viewer" is optional and deferred | OQ-20 (artifact storage); OQ-5 (is the Dell the Linux host); OQ-17 and F5 for `console-core`; a desktop viewer cannot offer "Scan an APK" | 2 (1 release lane, 1 `console-core`); viewer ≈1 extra, not in the program's §5 row | PLAN |
| iOS / iPadOS | reframe | Pipeline `NOT-APPLICABLE` (no arbitrary process spawning, git or podman in the iOS sandbox; App Review 2.5.2 also bars executing downloaded code). Console: a read-only "snapshot viewer" in Compose Multiplatform over `console-core`, thin SwiftUI shell, import via `UIDocumentPicker`, strict validation, trust banners, copy-command templates, "Explore a sample"; iPad first; no APK quick check; no keys, so no Keychain actual | R4 (`console-core` first); OQ-2; low value before V2 Epic B or Orrery (OQ-30); R11 (no bundle id); no entitlement can express "no Internet", so it is a code-level and README statement | 4 | PLAN |
| macOS | straightforward, only if a Linux viewer exists | The same JVM viewer as a DMG via `jpackage` on `macos-latest`, unsigned until OQ-3; "Designed for iPad" presence if an iOS viewer exists. Engine and CLI: `NOT-APPLICABLE (Linux-only runner by contract)`. If the owner rules the CLI is the desktop surface, the row is `not-applicable`, 0 | Depends on the deferred Linux viewer; no Mac on record (OQ-5), so device gates are NOV; OQ-3 | 1.5 (0 if no viewer) | PLAN |
| Windows | straightforward, only if a Linux viewer exists | The same JVM viewer as a per-machine WiX MSI on `windows-2025` + winget manifest, unsigned until OQ-3. Engine and CLI: `NOT-APPLICABLE (Linux-only runner by contract; NTFS drops the owner-only guarantee, §2)` | Depends on the viewer; the Dell is the only Windows machine and is slated for Linux (OQ-5); OQ-3; R3 path lint first | 1.5 (0 if no viewer) | PLAN |

The estimates total 3 + 2 + 4 + 1.5 + 1.5 = 12 engineer-weeks, overlapping rather than additive; the program's §7 counts them in no effort-envelope column.

## 5. Tier and sequencing

**Tier C (thin-or-reframe), as in the program's §5 row.** The product is a pipeline that already runs on Linux CI and is Linux-only by contract. The one end-user surface splits
into a portable validator, a UI that would be a small rewrite, and an APK check that cannot be ported honestly; off-Android viewers duplicate the CLI on Linux, and iOS, macOS and
Windows have no persona. So `console-core` plus a CLI release lane now (≈2w), and every viewer UI is a "snapshot viewer" reframe, deferred behind (1) the canonical-PR ruling, (2)
the Orrery reconciliation (PT:D-W; if Orrery is the cross-platform viewer the Assay viewers are redundant) and (3) V2 Epic B (`docs/V2-ROADMAP.md`). The program's §7 lists no Assay
item in any wave, so this plan assigns none beyond P-LX for L1 and L2. Every step in §6 is a proposal for the owner, not a ruled decision: the repo keeps no decision log, so if
adopted they would enter `docs/V2-ROADMAP.md` (beside Epic B) through the canonical PR, not from this port track.

| Platform | Program wave | Repo-local gate that must hold first |
|---|---|---|
| Ubuntu Touch | None in §7. Shape (a) is owner-device evidence (OQ-21), outside any wave; shape (b), if wanted, would reuse F7's QML/webapp template (a proposal, not scheduled) | Canonical PR landed (OQ-30); OQ-21 answered; OQ-1 device or a CI-only waiver; a NAMES.md row |
| Linux desktop | P-LX, for L1 and L2 only | `gradle check --no-daemon` green on the canonical PR; OQ-17 and F5 for `console-core`; OQ-20 for the release lane |
| iOS / iPadOS | P-iOS, late; unscheduled | `console-core` green on hosted Linux (R4); a viewer ruled wanted (OQ-30); V2 Epic B landed (the program's §5 row) and the Orrery decision; OQ-2 |
| macOS · Windows | P-mac · P-win, conditional | A Linux viewer exists, else `NOT-APPLICABLE`; OQ-3; OQ-5; for Windows also the R3 path lint |

## 6. Work breakdown

**Placement (R1-R3).** New top-level directories only: `console-core/` (a third independent Gradle build beside the root build and `android-console/`, pure Kotlin, R4), `viewer/`
(Compose UI, only if a viewer is approved), `packaging/{linux,macos,windows}/`, `apple/`, `ubuntu-touch/`. `src/`, `android-console/`, `schemas/`, `ops/` and the three existing
workflows are untouched by a port track; the root `settings.gradle.kts` only names the project and stays as is. Each new workflow is a new file with SHA-pinned actions, a
root-unchanged diff check, package jobs on `main`/tags only and no Actions artifact (R6). The planning container compiles JVM x86_64 only, so only `installDist` in L1 and L2 can
run here (`CONTAINER-BUILD-ONLY`). **G0 (gate):** the canonical PR (OQ-30) lands on `main`; nothing below starts earlier.

**Linux desktop**

- **L1 CLI release lane (≈1w).** `packaging/linux/`: `build-cli.sh` (`gradle installDist`, then a jlink image sized with `jdeps`), `install.sh`, a README stating "UNSIGNED — not
  for release"; output `assay-<tag>-linux-x86_64.tar.gz` + `SHA256SUMS`. Scanners and `tool-lock.json` are not bundled. New `.github/workflows/release-cli.yml`, tags and manual
  dispatch only, draft GitHub Release, no signing step (a disabled template until OQ-3). Done when a tag run attaches both files and a scanner-free command (`assay help`) runs from
  the unpacked image on a clean runner: `CI (hosted VM)`. VERIFICATION's deployment item 7 (release signing, retention, distribution) stays pending.
- **L2 `console-core/` (≈1w).** `kotlin("multiplatform")`, `jvm()` only. Port `ConsoleSnapshot.kt` and `DemoData.kt` keeping every regex, the exact-key check, the 8 MB cap and the
  cross-reference rules. JSON plan default: a copy of the engine's strict `Json.kt` (259 lines, no imports) so the repo stays free of third-party libraries; whether it compiles
  unchanged in `commonMain` is unknown until tried; fallback kotlinx-serialization with a recorded licence. Golden fixtures in `console-core/src/commonTest/resources/golden/`
  (`-text` in a local `.gitattributes`): an accepting and a rejecting case for each of the ten rules in `docs/ANDROID-CONSOLE.md` "Verified snapshot validation", the four
  availability states, and non-strict JSON (duplicate key, single quotes, trailing comma) that Android's lenient `org.json` may accept and `Json.kt` rejects; stricter is the safe
  direction. New `.github/workflows/console-core.yml` (F9 `jvm-linux`). Done when green: `CI (hosted VM)`, for `console-core`'s parser only.
- **L3 Android console adopts `console-core`.** Through the repo's normal PR, not a port track (R1), keeping `gradle -p android-console :app:lintDebug :app:assembleDebug` green
  (R2). Only this step leaves one validator; until then two Kotlin implementations exist and the Android one has no tests (`android-console/` has none; `org.json` is unmocked on a
  host JVM). Needs OQ-17, F5 and a Kotlin-plugin answer (PR #2 shows AGP's built-in Kotlin is fragile). Not in the program's 2w; not estimated here.
- **L4 optional "snapshot viewer", deferred (≈1w, outside the 2w).** `viewer/` (Compose Desktop over `console-core`) + `packaging/linux/` per F10, X11 first, read-only, no APK
  scan, §3 trust labels before results. Starts only after Q2, Q3 and the IA-governance questions.

**Ubuntu Touch** (after G0; one of two shapes)

- **U1 Decide.** If OQ-21 says Waydroid is the answer, shape (a) is 0 engineering: an Assay section in F11's `DEVICE_CHECKLIST_UT.md` (quick check on a valid and a malformed APK,
  snapshot import and rejection, no network) that the owner runs (OQ-1); only a filled certification record reaches Level 3.
- **U2 Shape (b).** A NAMES.md row for the click name first (R11/OQ-25), then `ubuntu-touch/` with `clickable.yaml`, `manifest.json.in`, `apparmor.in` (common groups only:
  `content_exchange`, no `networking`). Validator: spike whether `console-core` compiled to JS runs in Qt 5.15's QML engine (unknown; outside the program's framework law, so an
  owner call); if not, hand-port the 182-line parser and run the same golden fixtures under `qmltestrunner`. Clipboard under confinement is unknown. New
  `.github/workflows/ubuntu-touch.yml` (Clickable image, non-blocking 26.04 canary): `CI-APPROX — NOT DEVICE EVIDENCE`; lifecycle, content-hub and rendering are NDV.

**iOS / iPadOS** (after G0, L2, an OQ-30 "viewer wanted" and OQ-2)

- **I1** Add `iosArm64` and `iosSimulatorArm64` to `console-core` and a `macos-latest` job running the golden fixtures as `iosSimulatorArm64Test`: `SIMULATOR`.
- **I2** `viewer/` shared screens (reusable by L4) and `apple/` (SwiftUI shell, XcodeGen `project.yml`): `UIDocumentPicker` via F6, the 8 MB cap checked before reading, snapshot in
  memory only (any cache gets `isExcludedFromBackup`), `PrivacyInfo.xcprivacy` declaring no tracking or collection, no network code. New `.github/workflows/ios.yml`,
  `CODE_SIGNING_ALLOWED=NO`, unsigned `.xcarchive`, no store job (R5, R6). Then an owner run on the iPad Pro M4 by the OQ-2 route: NOV/NDV.

**macOS and Windows** (conditional on L4; otherwise the rows read `not-applicable`)

- **M1** `packaging/macos/` jpackage DMG, `.github/workflows/desktop-macos.yml`, unsigned, `main`/tags, draft Release: `CI (hosted VM)`; device gate NOV (no Mac on record).
- **W1** First the R3 Windows path lint (today `git ls-files | grep -E '[:<>|?*"]'` on PR #1 matches nothing) and a Windows-JDK run of the `console-core` suite for charset and CRLF
  traps; then `packaging/windows/` WiX MSI + winget manifest and `.github/workflows/desktop-windows.yml`, unsigned: `CI (hosted VM)`. **Engine lanes are not planned.** If the owner
  approves (Q7), a `windows-latest`/`macos-latest` `gradle check` job is dev-portability evidence only, with expected failures written down first (preflight `host.linux`,
  POSIX-mode assertions, the unguarded directory sync, silent permission no-ops). (Predictions; none run.)

## 7. Shared foundation this repo consumes or provides

**Consumes.** F5 kmp-conventions and the OQ-17 pin (L2, L3). F9 CI matrix template (a `jvm-linux` lane for `console-core`; `macos-latest` and `windows-2025` lanes only for the
conditional steps). F10 packaging templates (jlink tarball + `install.sh` for the CLI, not Flatpak; jpackage DMG/MSI and XcodeGen only if a viewer exists). F11 evidence labels,
mapped onto VERIFICATION.md (§0), and `DEVICE_CHECKLIST_*` for a viewer only. F6, the file-picker actual only (the console holds no keys). F7, the QML/webapp template and apparmor
checker, only if UT shape (b) is chosen (the JVM bridge is not needed). F1 only if a viewer is built **and** OQ-29 makes Assay a Hyle consumer; PR #2 shows the cost on the Android
console (minSdk 26 to 31, AGP 9.3.0 to 8.9.1). **Does not consume:** F2, F3, F4, F8, F12. NAMES.md rows (R11) are needed for any click, bundle, MSI or winget identifier.

**Provides.** `console-core` (one validator for any viewer, and for Orrery if OQ-30 rules it a viewer); `schemas/` as the engine's source of truth; golden fixtures; the read-only
projections `console-snapshot`, `orrery-status` and the `fonebrew-propose` gateway. Shared-Libraries-asoc's `evidence-schema` is a candidate home for an Orrery-facing contract; it
has no F-item, so it is raised under OQ-30 only.

## 8. Open questions for the owner

Program questions relied on, not restated: OQ-1 (UT device), OQ-2 (Apple), OQ-3 (signing), OQ-17 (pins), OQ-20 (CI minutes, artifact storage), OQ-25 (identifiers).

1. **Canonical PR and merge order (OQ-30, PT:D-W).** #1 (draft foundation), #3 ("supersedes #1", publishes to a user-selected branch under `.assay/`) or #5 (stacked on #1); is #1
   closed or kept? No `assay/audit` branch exists, so confirm there is nothing to migrate. *Blocks:* G0, the counts in §2, where this plan is indexed.
2. **What Orrery reads, and is any viewer wanted before V2 Epic B (OQ-30).** The internal `orrery.json` 1.1.0 or #3's `.assay/assay-index.v1.json`? If Orrery is the cross-platform
   viewer no Assay viewer is ported and this plan collapses to `console-core` and the release lane. Recommendation: `console-core` only until the connected read model exists, since
   `assay verify-bus` and `console-snapshot` already serve the operator on Linux. *Blocks:* L4, I1-I2, M1, W1, U2.
3. **Does the registry's P6 still bind?** Its wording lives only in Personal-Tracker; the shipped screen lists findings read-only. Must any viewer drop the lists? (No master OQ id;
   proposed as part of the OQ-30 viewer ruling.) *Blocks:* the screen set of L4, U2 and I2.
4. **Dell runner against the no-second-machine rule (OQ-5).** The runner docs make the Dell the certification host; PT:D-C (2026-07-02) deferred it indefinitely. Options: the Dell
   as a Linux runner, a hosted `ubuntu-latest` runner (the threat model's "network denied during scan" needs a design there), Fonebrew-supplied execution via PR #5's boundary, or
   defer. *Blocks:* naming a production host, VERIFICATION deployment items 1-5.
5. **Hyle consumer (OQ-29) and the red/green hue roles.** Accept PR #2's price (minSdk 26 to 31, AGP downgrade, never compiled) or stay Hyle-free; must the hues become violet/cyan
   with shape? *Blocks:* F1 use, L3, every viewer palette.
6. **Waydroid as the UT answer (OQ-21).** It needs no GMS or network and would keep the APK quick check; acceptable, and is there a device (OQ-1)? *Blocks:* U1 versus U2.
7. **Engine beyond Linux x86_64.** (a) Run `gradle check` on `windows-latest`/`macos-latest` as dev evidence only? If yes, the NTFS permission and sync degradation is documented
   first so a green lane is never read as a supported runner (public repo: minutes free, OQ-20). (b) arm64 Linux for a runner? It needs new `linux_arm64` tool-lock digests and a
   contract run (`scanner-contracts.yml` pins x64 only). *Blocks:* any macOS or Windows engine lane; L1 arm64.
8. **APK quick check off Android.** Write an AXML/signature parser (new code) or label every non-Android viewer "snapshot only" (recommended). *Blocks:* viewer scope.
9. **Release lane against the three existing artifact uploads (OQ-20, R6).** The workflows upload Actions artifacts while storage is exhausted and `main` runs a six-hourly cleanup.
   Draft Releases for the CLI; should the canonical PR drop or shorten the uploads? Signing, retention and distribution stay pending. *Blocks:* L1.

## 9. Sources read

In PR #1 at fb150ab: `README.md`; `build.gradle.kts`, `settings.gradle.kts`, `.gitignore`; `.github/workflows/{ci,scanner-contracts,android-console}.yml`;
`android-console/{settings,build}.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `res/`,
`app/src/main/kotlin/dev/assay/{AssayHomeActivity,MainActivity,LocalApkAudit,ConsoleSnapshot,DemoData}.kt`;
`src/main/kotlin/dev/assay/{Main,Json,Pipeline,RunnerPreflight,Scanner,Redaction,IntegrationProjection,Bus,CandidatePersistence,GitBusPublisher,MobSfExecution}.kt`;
`src/test/kotlin/dev/assay/` (survey);
`docs/{ANDROID-CONSOLE,VERIFICATION,RUNNER-DECISION,RUNNER-OPERATIONS,V2-ROADMAP,INTEGRATIONS,CONTRACT,THREAT-MODEL,TOOLCHAIN,PRODUCT-EXPERIENCE,PERSONAS-AND-ROLES,INFORMATION-ARCHITECTURE,USER-JOURNEYS,CANDIDATE-LIFECYCLE}.md`;
`ops/`, `scripts/`, `schemas/`. GitHub REST (read-only, 2026-10-06): the PR and branch lists of `mbaliga/assay`; PR #2, #3 and #5 contents via the profile reader. Program side:
`Personal-Tracker/PORTING_PROGRAM.md` (§0-§3, §4 platform sections, the Assay row of §5, §6-§8) and `Personal-Tracker/{CONSTELLATION,NAMES,DECISIONS,STATE}.md` (Assay, Orrery, D-C,
D-W).


## Owner rulings and the proposed line (added 2026-10-07)

Status: PLAN. Nothing here is built, run on a device, signed or submitted. The program-level plan is Personal-Tracker `PORTING_PROGRAM.md` ([PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which holds the owner's rulings and section 5A, the proposed port / no-port line. The cells, estimates and open questions above are this repo's original plan and are unedited. Where the owner has since answered a question, the answer is below. Section 5A is a proposal; the owner has not yet confirmed it.

### Where assay sits in the proposed line (program section 5A.3, a proposal)

| Target       | Verdict | Weeks and flags |
| ------------ | ------- | --------------- |
| Ubuntu Touch | no-port | -               |
| Linux        | exists  | 2w              |
| iOS/iPadOS   | no-port | -               |
| macOS        | no-port | -               |
| Windows      | no-port | -               |

Key: `follows` means it ports only as far as the products that depend on it; `exists` means the program reads it as already running there, unverified (finish, verify and sign); flags: `g` gated on a prerequisite, `r` re-estimate or floor, `o` its own program, `s` scope note. The program's P4, P8, P12 and P13 gate whole columns or repos and are not flagged per cell. A port verdict counts the deliverable in the line; where this repo's plan calls a deliverable a reframe (program rule R12) it keeps that label. Tests cited in the reason: (a) the owner said it is needed there; (b) its job is really done on that OS by real users; (c) that OS is where it is sold or its audience is; it has no reason to exist if (x) its surface is absent or untouchable, (y) the capability is forbidden or impossible, or (z) the only form is a thin wrapper or a different product nobody asked for. P-numbers and OQ-numbers refer to the program plan (Personal-Tracker `PORTING_PROGRAM.md`, sections 5A.5 and 8).

Reason: The pipeline is a Linux CI role by contract (rootless podman, POSIX permissions); a snapshot viewer elsewhere has no value before V2 Epic B.

### Owner rulings that apply here

- **Ubuntu Touch scope (2026-10-06):** "Native only, no substitutes" for Android-only products (the program marks this repo's Ubuntu Touch cell as a substitute). This plan's own Ubuntu Touch cell above (the Waydroid option and a thin read-only snapshot-viewer click) is a substitute the ruling excludes, which is why the proposed verdict is no-port. **OQ-21:** "No, native ports only" (Waydroid is not accepted).
- **OQ-17 toolchain (2026-10-06):** "B: staged pin (Recommended)": the engine is already on Kotlin 2.1.20; the 2-week Linux cell adds console-core (kotlin("multiplatform"), jvm() only, no Compose), which takes the Kotlin part of the pin, 2.4.x deferred. The Compose Multiplatform 1.8.2 part matters only if the deferred viewer is built; L3 (the Android console adopting console-core) still needs F5 and a Kotlin-plugin answer.
- **OQ-20 CI (2026-10-06):** "Linux-only CI when private (Recommended)": this repo is public, so the ruling does not limit its macOS and Windows lanes; going private would stop them. Actions artifact storage is still exhausted (program rule R6).
- **OQ-5 hardware (2026-10-06):** the owner's answer changes which of their other machines can serve as device gates, so a gate this plan names on specific hardware may be moved or dropped. Which machine carries which device gate is not decided (OQ-33). The plan's statement that the Dell is the only Windows machine and is slated for Linux is superseded; whether the Dell is Assay's runner host (plan Q4) stays open under OQ-33, which names Assay.
- **Directives (2026-10-06):** "Draft amendments for approval": program directives I-1 to I-12 and rules R1 to R12 are unchanged; PROPOSED-1 to PROPOSED-4 in Personal-Tracker `DECISIONS.md` are drafts awaiting the owner.

### Prerequisites and open questions that touch this repo (program sections 5A.5 and 8)

No program-level prerequisite is named for this repo.

Owner questions in the program register that concern this repo (status as of 2026-10-07):

- OQ-5 (ruled): Hardware stance
- OQ-17 (ruled): Toolchain pins: the pin is ruled; the "Also" approvals (converting shared modules to kotlin("multiplatform"), asom's no-KMP rule staying asom-local) are unanswered
- OQ-20 (ruled): CI minutes, storage and repo visibility
- OQ-21 (ruled): Waydroid as the Ubuntu Touch answer (the program reads Assay as Android-only for this)
- OQ-29 (open): Hyle-consumer status
- OQ-30 (open): Assay: canonical PR and what Orrery reads
- OQ-33 (open): Hardware details still open

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A change.
