# Assay contracts

Assay has two layers of contract, deliberately kept separate:

1. **The ratified constellation contract (canonical, external)** — what Fonebrew Core reads, per
   the ratified `docs/ratified/ASSAY_REPO_CONTRACT_V1.md` (INT-018/019/020) in the Core repo.
   Core is the source of truth for this shape; Assay conforms to it. See "Ratified output
   contract" below.
2. **The internal trust-engine bus (contract 1.1.0, described in the rest of this document)** —
   Assay's own multi-run history, per-finding lifecycle, and proof-evidence store. This keeps
   Assay's candidate lifecycle (`docs/CANDIDATE-LIFECYCLE.md`), console/Orrery projections, and
   proof verification working exactly as before. It is intact and unchanged.

The two are related but not identical: the trust-engine bus is Assay's internal working state
(full run history, `activeRuns`, per-finding lifecycle); the ratified output is the narrow,
single-run, externally-publishable *projection* of that state Core actually reads. Prior to this
document, Assay published its internal bus shape directly as its external contract — that was the
drift this document now resolves. The internal shape did not change; a new, ratified-shape
writer/publisher sits alongside it and is what gets pushed for Core to read.

## Ratified output contract (external, canonical — conforms to Fonebrew Core)

Assay writes its scan output under `.assay/` on a **user-selected output branch** of the scanned
repository (not a name Assay hardcodes) — [`AssayOutputWriterV1`](../src/main/kotlin/dev/assay/AssayOutputWriterV1.kt)
stages the tree, [`GitBusPublisher.publishV1`](../src/main/kotlin/dev/assay/GitBusPublisher.kt)
publishes it (CLI: `assay publish-output-git`, see `assay help`):

```text
.assay/
  assay-index.v1.json          <- schemas/integrations/assay-index.v1.schema.json
  runs/<run-id>/
    findings.sarif              SARIF 2.1.0, this run's canonical findings
    proving-tests.v1.json       <- schemas/integrations/proving-tests.v1.schema.json
    evidence/                   supporting artifacts a finding/proving test references
  tests/proving/                 generated proving-test SOURCES
  README.md                      human instructions; no hidden control data
```

`assay-index.v1.json`'s required fields (`schemaVersion`, `runId`, `projectRef.gitRemote`,
`sourceCommit`, `assayCommit`, `tool`, `startedAt`/`finishedAt`, `findingFiles[]`, `provingTests`,
`completeness`, and `explanation` whenever `completeness` is `PARTIAL`/`FAILED`) mirror
[`AssayIndexV1`](../src/main/kotlin/dev/assay/AssayContractV1.kt) field-for-field; the JSON Schema
is mirrored locally at `schemas/integrations/assay-index.v1.schema.json` /
`proving-tests.v1.schema.json`, with matching fixtures under `fixtures/integrations/{valid,invalid}/`
copied verbatim from Core. `AssayContractV1AcceptanceTest` asserts our required-field lists,
regex patterns, and enums are byte-identical to the committed schema files (a structural
assertion test — this project has no JSON-Schema validator dependency), decodes Core's fixtures,
and validates our own writer's output the same way.

**`assayCommit` and hash self-reference.** The field must name "the commit ON the output branch
that carries this exact index" — but a cryptographic hash cannot generally contain its own value
(the index's bytes are part of the tree the commit hashes over). `publishV1` resolves this in two
passes folded into a single `git push`: it builds a provisional commit `P` with a placeholder,
then a corrected commit `F` — parent `P` — with the placeholder replaced by `P`'s real hash. Only
`F` is pushed as the branch tip (which transfers `P` too, as `F`'s parent, since `git push`
carries every object reachable from the pushed ref); `assayCommit` in `F`'s index therefore names
a real, fetchable, immediate-parent commit whose tree is byte-identical to `F`'s except for that
one field. See the doc comment on `GitBusPublisher.publishV1` and
`AssayOutputPublisherAcceptanceTest` for the mechanics and a chained-publish verification.

Orphan-branch behaviour (no parent commit when the remote branch doesn't exist yet) still falls
out of the same `commit-tree`/`push --force-with-lease` plumbing when the user-selected branch is
new — it's an implementation detail of how the publisher builds history, not a name Assay forces.

## Migration map (internal bus shape → ratified output shape)

| Internal bus (1.1.0, `Bus.kt`) | Ratified output (`AssayContractV1.kt`) | Note |
|---|---|---|
| orphan branch `assay/audit` (hardcoded) | `branch` param, user-selected | `GitBusPublisher.branch` default kept for the internal bus only |
| `index.json` at branch root, `schemaVersion: "1.1.0"` (rolling `activeRuns`/`runs[]`/`latestComplete` history) | `.assay/assay-index.v1.json`, `schemaVersion: "1.0.0"` (single current run) | full history stays internal; the ratified index is a narrow per-run projection |
| `runs/<run-id>/run.json` (per-scanner metadata: digest/exitCode/format) | *(not part of the ratified shape — `tool.name`/`tool.version` only)* | scanner-level detail stays internal/in SARIF `properties` |
| `runs/<run-id>/status.json` (per-finding lifecycle + `provingId`) | *(not part of the ratified shape)* | lifecycle stays internal (`ContractValidator`, `Candidate.kt`); SARIF `properties.provingTestRef` carries the pointer externally |
| `runs/<run-id>/findings.sarif` | `.assay/runs/<run-id>/findings.sarif`, referenced via `findingFiles[]` (path/sha256/count) | `Sarif.encode` now takes an optional `provingTests` argument; every `results[].properties` also carries the ratified §4 pair `scannerName`/`ruleId` (additive, alongside the pre-existing `originatingScanner`), plus `provingTestRef` when a proving test's `targetFindingRef` names that result positionally (`findings.sarif#/runs/0/results/<i>`) -- otherwise omitted, per §4's "absent is legal". Findings are still summarized, not repeated, in the index |
| `runs/<run-id>/manifest.sha256` (whole-run digest) | *(not part of the ratified shape)* | per-file `findingFiles[].sha256` replaces a whole-run manifest for the externally-read subset |
| — (proof evidence lived in `ProofEvidence`/candidate records only) | `runs/<run-id>/proving-tests.v1.json` (`ProvingTestsV1`) | new: the ratified contract's proving-test summary, designed in `ASSAY_REPO_CONTRACT_V1.md` §3 |
| `index.json.runs[].gate` (`provingTestsRequired/Passed/passed`) | *(not part of the ratified shape)* | gate bookkeeping stays internal |

## Internal trust-engine bus contract 1.1.0

The bus is the orphan branch `assay/audit`. Its root index is `index.json`; completed runs live under `runs/<run-id>/`. Canonical findings are SARIF 2.1.0.

## Required run layout

```text
index.json
runs/<run-id>/
  run.json
  findings.sarif
  status.json
  manifest.sha256
  scanners/<scanner>.<sarif|json>
```

Every scanner artifact is fully redacted before publication. `findings.sarif`, `status.json`, `run.json`, and every scanner artifact are listed in `manifest.sha256`. The manifest does not list itself; its digest is stored in the index.

## Identity

A finding fingerprint is SHA-256 over a UTF-8 record in this exact order, separated by U+001F:

1. `assay-fingerprint-v1`
2. scanner wire name
3. normalized lowercase rule ID
4. normalized slash-separated relative path
5. start line
6. end line
7. redacted NFC-normalized context

A proving ID is `pt1-<scanner>-<rule-slug>-<first16hex>`. Consumers store it byte-for-byte and otherwise treat it as opaque.

## Index and lifecycle

`latestComplete` is nullable. Running jobs belong in `activeRuns`; completed and failed jobs belong in `runs`. One bus root belongs to exactly one `sourceRepo`. Reusing it for another repository is invalid.

A newly detected finding is unresolved. Therefore a completed run containing findings has:

- `provingTestsRequired = number of findings`;
- `provingTestsPassed = 0`;
- `passed = false`.

An empty, valid run may use `0 / 0 / true`. A writer must not mark a finding proven merely because a proposed proof exists; proof evidence and lifecycle updates are separate artifacts in later phases.

## Reader order

A reader must fail closed and perform these checks in order:

1. parse and validate `index.json`;
2. select evidence for the exact requested source commit;
3. validate run paths and cross-file references;
4. verify the manifest digest;
5. verify every listed file digest and size;
6. reject symlinks, traversal, absolute paths, unlisted files, and size-limit violations;
7. validate `run.json` and `status.json`;
8. parse canonical SARIF and recompute summary/gate invariants.

Typed reader states are `NotConfigured`, `Unavailable`, `Invalid`, and `Ready`. The first three are never represented as an empty finding set.

## Proof applicability

A passing proof requires the exact source commit, distinct before/after tree refs, non-empty patch and test digests, scanner configuration and toolchain digests, at least two identical before attempts and two identical after attempts, evidence that the named test ran, assertion failure before, pass after, scanner reproduction before, and scanner clearance after. Proving-ID equality alone is never sufficient.

Machine-readable schemas live in `schemas/`.
