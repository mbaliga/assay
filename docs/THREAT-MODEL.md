# Assay v1 threat model

## Trust boundaries

The audited repository, scanner binaries and containers, scanner configuration and rule packs, AI adapter, runner host, SSH transport, GitHub credential, repository bus, and every bus consumer are separate trust boundaries.

## Execution

Assay assumes every audited repository is malicious. Scanner and build execution must occur in an ephemeral unprivileged outer sandbox with no host SSH agent, Docker socket, home directory, cloud credentials, or network by default. The Kotlin core does not execute repository build scripts. A production runner introduces a scoped push credential only after evidence validation.

A tool-lock claim is not sufficient by itself. Local scanner execution verifies the executable SHA-256 and probes the scanner-reported version before scanning. Semgrep uses a pinned local rule configuration with metrics and version checks disabled. OSV is offline by default. MobSF must be pinned by immutable image digest and isolated as untrusted execution.

## Evidence

Scanner output, deleted patch lines, logs, snippets, and model prompts may contain credentials or host paths. Reports are redacted immediately after capture and rewritten in redacted form. Publication performs a second secret scan over the complete staged bus. Raw unredacted evidence must not be committed to the public audit branch.

The bus is hostile input to every reader. Readers reject unsupported schemas, source-repository or source-commit mismatches, traversal, absolute paths, symlinks, missing or extra files, digest mismatch, forged summaries, contradictory gates, stale evidence, failed runs, and size-limit violations. `Unavailable` and `Invalid` never mean zero findings.

## AI and fixes

AI receives immutable deterministic finding views and cannot construct findings. Proposed patches remain inert until repeated mechanical proof passes and a human approves a dedicated fix branch. Rejection commits nothing. Nothing auto-merges.
