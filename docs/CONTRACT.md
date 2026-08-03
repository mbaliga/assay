# Assay repository-bus contract 1.1.0

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
