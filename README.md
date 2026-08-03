# Assay

Assay is a standalone deterministic security-audit and proof engine for the Fonebrew constellation. It writes versioned, integrity-checked SARIF evidence to a repository bus. AI is optional and downstream; it never originates findings.

## v1 scope

This release provides the implementation-safe core: stable finding identity, proving IDs, deterministic secret scanning fixture, SARIF normalization, full redaction, atomic local bus publication, manifest verification, typed fail-closed reader states, proof validation, approval lifecycle, schemas, threat model, CLI, acceptance suite, and CI artifact packaging.

It deliberately does **not** claim the Dell runner, real Gitleaks/Semgrep/OSV/MobSF adapters, Android console, Fonebrew connector, or orphan-branch remote publication are verified. Those require their actual environments and repositories. The core is designed so those integrations attach without changing the trust model.

## Verify

```bash
./scripts/test.sh
# or
gradle check
```

## CLI

```bash
assay fixture --root /tmp/vulnerable
assay scan --repo /tmp/vulnerable --bus /tmp/bus --source-repo example/repo --source-commit aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
assay verify-bus --bus /tmp/bus
```

Status ceiling: `ready-for-human-review`; nothing auto-merges.
