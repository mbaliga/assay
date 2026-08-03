# Assay

Assay is a standalone deterministic security-audit and proof engine for the Fonebrew constellation. It converts scanner evidence into versioned, integrity-checked SARIF on a repository bus. AI is optional and downstream; it cannot originate findings or publish evidence.

## Current v1 scope

The v1 core now includes:

- stable SHA-256 finding identities and lowercase proving IDs;
- pinned adapters for Gitleaks, Semgrep, OSV-Scanner, and MobSF JSON reports;
- binary-digest and reported-version verification before a local scanner is run;
- noninteractive command construction with Semgrep telemetry disabled and OSV offline by default;
- deterministic multi-scanner normalization to SARIF 2.1.0;
- one versioned SARIF-to-SEV policy with pinned severity floors;
- full evidence redaction, staged-bus secret scanning, manifests, size limits, and atomic local publication;
- typed fail-closed bus reader states, cross-file validation, tamper detection, stale-commit rejection, and repository ownership checks;
- repeated fail-before/pass-after proof requirements and an explicit human approval lifecycle;
- strict JSON Schemas, CLI commands, acceptance tests, and GitHub CI packaging.

The Dell runner, remote orphan-branch push-with-lease, MobSF container execution, Android console, Fonebrew connector, and Orrery connector still require their actual environments. They are not falsely certified by this repository.

## Verify

```bash
./scripts/test.sh
# or, with Gradle installed:
gradle check
```

## CLI

Create and scan the deterministic fixture:

```bash
assay fixture --root /tmp/vulnerable
assay scan-fixture \
  --repo /tmp/vulnerable \
  --bus /tmp/bus \
  --source-repo example/repo \
  --source-commit aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
assay verify-bus --bus /tmp/bus --expected-source-commit aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

Run a pinned external scanner:

```bash
assay run-scanner \
  --scanner gitleaks \
  --tool /opt/assay/tools/gitleaks \
  --tool-lock /opt/assay/tool-lock.json \
  --target /work/source \
  --raw-output /work/evidence/gitleaks.sarif \
  --canonical-output /work/evidence/gitleaks.canonical.sarif
```

Normalize or merge pre-existing reports:

```bash
assay normalize --scanner mobsf --input mobsf.json --root /work/source --output mobsf.sarif
assay merge \
  --root /work/source \
  --input gitleaks=gitleaks.sarif \
  --input semgrep=semgrep.sarif \
  --input osv=osv.sarif \
  --input mobsf=mobsf.json \
  --output findings.sarif
```

See `docs/CONTRACT.md`, `docs/TOOLCHAIN.md`, `docs/THREAT-MODEL.md`, and `docs/VERIFICATION.md` before integrating a runner or reader.

Status ceiling: `ready-for-human-review`; nothing auto-merges.
