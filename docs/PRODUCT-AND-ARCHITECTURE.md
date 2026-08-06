# Assay — product and architecture

A deterministic security-audit, proof and remediation-control system for Android repositories. It
converts scanner evidence into integrity-checked SARIF, publishes a disconnected audit history,
verifies proposed fixes against a proving test, requires explicit human decisions, and exposes
read-only projections to the Fonebrew constellation.

**AI is optional and downstream. It cannot originate findings, record proof, approve candidates,
apply fixes or publish audit evidence.**

> **This document is the orientation map.** Assay's normative detail lives in thirteen sibling
> documents, and this one deliberately does not restate them — it explains the shape of the system
> and points at the document that governs each part. Where a sibling doc is normative and this one
> is descriptive, **the sibling wins**.

| If you want… | Read |
|---|---|
| What the bus format *is*, normatively | [`CONTRACT.md`](CONTRACT.md) |
| How a finding becomes a verified fix | [`CANDIDATE-LIFECYCLE.md`](CANDIDATE-LIFECYCLE.md) |
| How information is grouped and presented | [`INFORMATION-ARCHITECTURE.md`](INFORMATION-ARCHITECTURE.md) |
| Product intent and experience principles | [`PRODUCT-EXPERIENCE.md`](PRODUCT-EXPERIENCE.md) |
| The Android app's trust boundary and APK rules | [`ANDROID-CONSOLE.md`](ANDROID-CONSOLE.md) |
| Who does what, human and machine | [`PERSONAS-AND-ROLES.md`](PERSONAS-AND-ROLES.md) |
| End-to-end journeys | [`USER-JOURNEYS.md`](USER-JOURNEYS.md) |
| Adversaries and boundaries | [`THREAT-MODEL.md`](THREAT-MODEL.md) |
| Why the runner is what it is | [`RUNNER-DECISION.md`](RUNNER-DECISION.md) |
| Running one | [`RUNNER-OPERATIONS.md`](RUNNER-OPERATIONS.md) |
| Pinned tool versions | [`TOOLCHAIN.md`](TOOLCHAIN.md) |
| How claims are proven | [`VERIFICATION.md`](VERIFICATION.md) |
| Constellation integration | [`INTEGRATIONS.md`](INTEGRATIONS.md) |
| What v2 adds | [`V2-ROADMAP.md`](V2-ROADMAP.md) |

---

## 1. Features

### The deterministic audit engine

Pinned **Gitleaks**, **Semgrep** and **OSV-Scanner** execution, plus **MobSF** upload/scan/retrieve/
cleanup. What makes it an *audit* rather than a scan run:

- **Executable SHA-256 and reported-version verification** before anything runs — a tool that is
  not the pinned tool does not execute.
- **Immutable MobSF container-image pins** and least-privilege container command generation.
- **Noninteractive execution**, Semgrep telemetry disabled, OSV offline by default.
- **Deterministic multi-scanner SARIF 2.1.0 normalisation and merge** — the same inputs produce
  byte-identical output.

### The evidence bus

An orphan git branch (`assay/audit`) carrying an append-only audit history: a root `index.json`,
completed runs under `runs/<run-id>/`, and integrity-checked documents throughout. It is
*disconnected* by construction — the audit history shares no history with the source tree it
describes, so rewriting the source cannot rewrite its audit.

`verify-bus` re-checks the whole structure; `ContractValidator` enforces the schema.

### The candidate lifecycle

A finding is immutable scanner evidence. A **candidate** is a separately identified proposed
remediation, and it never replaces the finding it addresses. Its states:

```text
DETECTED → PROPOSED → PROOF_PASSED ─┬─▶ APPROVED → APPLIED
                   └─ PROOF_FAILED  └─▶ REJECTED
                                    (also: STALE, SUPERSEDED, WITHDRAWN, OBSOLETE)
```

Three separations are load-bearing and stated as rules, not conventions:

1. **Proof is not approval.** A passed proving test is machine evidence; a human still decides.
2. **Approval is not application.** A decision is recorded before anything touches a branch.
3. **A candidate carries its own identity, patch, proving test, branch and fix commit** — so what
   was approved is exactly what is applied, and both are checkable after the fact.

### The Android console

The user-facing review surface (`android-console/`, package `dev.assay`). It requests **no
Internet permission** and opens on three explicit paths:

| Path | What it does | Trust label |
|---|---|---|
| **Scan an APK** | Bounded, offline, package-level inspection of an APK the user picks | *Local quick check* |
| **Open verified audit** | Imports and strictly validates a runner-produced projection | *Runner-verified evidence* |
| **Explore a sample project** | Built-in demonstration data | *Sample data* |

The local quick check reviews package identity, version, SDK range, size and SHA-256; signing-
certificate presence; debuggable/test-only, backup and cleartext-traffic flags; dangerous and
broad permissions; unguarded exported components; and native-library presence. It copies the APK
to a private temporary file, inspects it, and **deletes the copy**.

It is labelled *Local quick check* deliberately and permanently. It runs none of the four
scanners, inspects no source history, and creates no tamper-evident evidence.

### Narrow integrations

Read-only projections to the Fonebrew constellation (`IntegrationProjection`), plus `Redaction`
for what may cross a boundary. Assay stays independently useful; integration is a projection, not
a coupling.

---

## 2. Interaction patterns

### Trust level is the first thing you see

Assay's whole interaction posture follows from one rule: **a surface must never let the user
mistake what kind of thing they are looking at.** The four states stay visually and semantically
distinct — *Local quick check*, *Runner-verified evidence*, *Sample data*, *Unavailable or
invalid evidence* — and the fourth is never softened into "zero findings". An audit that could not
run says so.

### Read-only means read-only, visibly

The Android app **cannot** originate findings, record proof, approve, reject, apply or merge. For
a proof-passed candidate it may **copy an explicit CLI command** to the clipboard, and execution
happens on the trusted runner where it is revalidated. Authority is reflected in what the
navigation offers, not just in what the backend enforces — a surface without authority does not
display buttons implying it.

### Contract language, not softer synonyms

UI copy uses the contract's own state names. A blocked gate says blocked; an invalid projection
says invalid. This is an explicit IA principle
([`INFORMATION-ARCHITECTURE.md`](INFORMATION-ARCHITECTURE.md) §9) because inventing gentler
wording is exactly how a security tool becomes untrustworthy.

### Evidence state precedes results

The user learns whether evidence is ready, unavailable, invalid, stale or not configured **before**
seeing findings. Findings shown without that context invite the reading that an absent finding
means a clean repository.

### The CLI is the authority surface

Every state transition is a CLI verb on the runner:

```text
assay scan | normalize | merge | verify-bus | run-scanner | verify-tool-lock | print-command
assay candidate-create | -list | -show | -propose | -prepare | -proof
                       | -approve | -reject | -apply | -stale
assay console-snapshot --bus … --candidates … --expected-source-commit … --output …
```

`print-command` exists so an operator can see exactly what would run before it runs — the same
posture as the app's copy-command affordance.

---

## 3. Information architecture

The normative version is [`INFORMATION-ARCHITECTURE.md`](INFORMATION-ARCHITECTURE.md). The shape:

```text
Local Android inspection            ← never enters the runner hierarchy
└── Selected APK
      └── APK quick-check report
            ├── Package overview
            └── Local observations

Runner-verified system
└── Project / repository
      └── Source revision
            ├── Audit run
            │     ├── Scanner executions
            │     ├── Evidence state and gate
            │     ├── Findings                    ← immutable evidence
            │     └── Audit publication
            └── Finding
                  └── Remediation candidate       ← proposal, never a replacement
                        ├── Patch and proving test
                        ├── Candidate branch and fix commit
                        ├── Proof result
                        └── Human decision
```

The separations that define it:

1. **Local observations and runner findings never merge.** A package-level quick check must not
   enter the finding or candidate hierarchy — different provenance, different trust, different
   tree.
2. **Findings and candidates are separate objects.** Evidence versus proposal.
3. **Proof and approval are separate stages.**
4. **Source context is always attached.** Every run, finding and candidate is understood against
   an exact repository and source commit — which is why `console-snapshot` takes
   `--expected-source-commit` and refuses a mismatch.
5. **Operational diagnostics live apart from review.** Maintainers see a concise state; operators
   can go deeper without that noise reaching the review surface.

---

## 4. Software architecture

This is the material the sibling docs do not cover, so it is given in full here.

### Two independent builds

```text
assay/
├── src/                 JVM scanner + trust engine        (Kotlin/JVM, no Android)
├── android-console/     the review app                    (independent Gradle project)
├── schemas/             JSON Schema for every bus document
├── ops/                 systemd units, tmpfiles.d
└── scripts/             runner-preflight.sh, test.sh
```

`android-console/` is a **separate Gradle project on purpose**, so Android dependencies cannot
reach — or perturb — the JVM engine that produces evidence. The engine is buildable and testable
on a machine with no Android SDK at all.

### Engine composition

| File | Responsibility |
|---|---|
| `Main.kt` | CLI dispatch for the scan/normalise/merge/verify surface |
| `CandidateCli.kt` | CLI dispatch for the ten candidate verbs |
| `Model.kt` | The domain: `Finding`, `RunRecord`, `Severity`, `Lifecycle`, `ToolPin`, `ScannerCommand`, `ProofEvidence`, `BusState`, `ScannerOutcome` |
| `Scanner.kt`, `Pipeline.kt` | Scanner definitions; `ScanPipeline` bundling and tool-lock enforcement |
| `Sarif.kt` | SARIF 2.1.0 normalisation and deterministic merge |
| `MobSf.kt`, `MobSfExecution.kt` | MobSF API flow and container command generation |
| `Bus.kt`, `GitBusPublisher.kt` | The append-only evidence bus and its git publication |
| `ContractValidator.kt` | Schema and structural enforcement |
| `Candidate.kt`, `CandidatePersistence.kt` | Candidate records, events, approvals, applications |
| `Proof.kt`, `ProofDocument.kt` | Proving-test execution and its evidence document |
| `Fingerprint.kt` | Stable finding identity across runs |
| `Redaction.kt`, `IntegrationProjection.kt` | What may cross a boundary, and the read-only projections |
| `RunnerPreflight.kt` | Environment verification before a run is permitted |

### Determinism as an architectural property

Every design choice below exists to make two runs over the same inputs produce the same bytes:

- **Tool pins with SHA-256 + reported-version checks.** An unpinned or mismatched executable is a
  hard failure, not a warning.
- **`Fingerprint`** gives a finding stable identity across runs, so history is comparable rather
  than merely accumulated.
- **Normalisation before merge.** Each scanner's output is normalised into one SARIF shape before
  merging, with deterministic ordering.
- **Offline by default.** OSV offline, Semgrep telemetry disabled, noninteractive execution
  throughout.
- **JSON Schema for every document** (`schemas/*.schema.json`), validated by `ContractValidator`
  rather than trusted.

### The trust boundary, as code

The runner is a one-shot Kotlin/JVM process; **SSH is transport, not the trust boundary**
([`RUNNER-DECISION.md`](RUNNER-DECISION.md)). The boundary is enforced by what each surface can
*do*:

- The engine writes evidence; the app cannot.
- The bus is append-only and integrity-checked; a rewritten source tree cannot rewrite it.
- `console-snapshot` produces a projection that the app **strictly validates** before display, and
  which is bound to an expected source commit.
- The app has **no Internet permission** — it cannot fetch a projection, so a projection must be
  handed to it deliberately through an authenticated channel.

### Testing

Assay's test suite is written as **acceptance tests beside the units they cover** —
`AcceptanceTest.kt`, `CandidateAcceptanceTest.kt`, `CandidateCliAcceptanceTest.kt`,
`CandidatePersistenceAcceptanceTest.kt`, `GitBusPublisherAcceptanceTest.kt`,
`IntegrationProjectionAcceptanceTest.kt`, `MobSfExecutionAcceptanceTest.kt`,
`RunnerPreflightAcceptanceTest.kt`. [`VERIFICATION.md`](VERIFICATION.md) defines the three levels
of claim and what evidence each requires.

`scripts/test.sh` runs the engine suite; `scripts/runner-preflight.sh` verifies a runner host.
CI additionally lints and assembles the Android console.

### Android console internals

Deliberately small — six Kotlin files:

| File | Responsibility |
|---|---|
| `AssayHomeActivity.kt` | The three start paths, all rendering, trust labelling |
| `ConsoleSnapshot.kt` | The strict projection model and its validation |
| `LocalApkAudit.kt` | Bounded offline APK inspection |
| `DemoData.kt` | Clearly-labelled sample data |
| `MainActivity.kt`, `ViewTextCompat.kt` | Entry point and view helpers |

It builds views directly rather than through a heavy UI framework, consistent with a surface whose
job is to display validated evidence without inventing any. It consumes `hyle-design-system` as a
submodule for tokens; the palette is sourced from `HyleTokens` rather than hardcoded hex.

`minSdk 31`, with pre-API-28 fallbacks removed as dead. The AGP version must stay in lockstep with
the Hyle submodule's, or the composite build hard-fails.

---

## 5. Known limits

- **CI debug APKs use a CI-generated debug signing key.** Upgrading from an earlier downloaded
  build can report a signature conflict; uninstall the old debug app first. Production
  distribution requires a stable release-signing key.
- **The local quick check is package-level only.** No source history, no secret scanning, no
  dependency analysis, no tamper-evident evidence. This is a permanent boundary, not a gap to
  close.
- **The app cannot fetch evidence.** With no Internet permission, a projection must be transferred
  deliberately through an authenticated channel.
- **v1 is a one-shot runner process.** Continuous operation, multi-project fleets and the wider
  integration surface are [`V2-ROADMAP.md`](V2-ROADMAP.md).
- **AI remains downstream and optional** by design, and no roadmap item changes that.
