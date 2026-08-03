# Assay repo bus contract 1.1.0

Well-known branch: `assay/audit`. Index: `index.json`. Run root: `runs/<run-id>/`. Canonical findings: `findings.sarif` (SARIF 2.1.0).

`latestComplete` is nullable. Running jobs live in `activeRuns`. Consumers first validate `index.json`, then the run manifest digest, then each listed artifact digest before opening it. Absolute paths, traversal, symlinks, unlisted files, stale source commits, and size-limit violations are invalid.

Finding fingerprints are SHA-256 over a UTF-8 record in fixed field order separated by U+001F: algorithm version, scanner, normalized rule ID, normalized slash-separated location, start line, end line, and redacted normalized context. Proving IDs are lowercase `pt1-<scanner>-<rule-slug>-<first16hex>`.

Proof applicability requires source commit, patch digest, test digest, scanner configuration digest, toolchain digest, structured evidence that the named test ran, assertion failure before, pass after, and deterministic scanner reproduction before/clearance after. ID equality alone is never sufficient.
