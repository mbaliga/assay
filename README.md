# Assay

Assay is a standalone deterministic security-audit, proof and remediation-control system for Android repositories. It converts scanner evidence into integrity-checked SARIF, publishes a disconnected audit history, verifies proposed fixes, requires explicit human decisions and exposes read-only projections to the Fonebrew constellation.

AI is optional and downstream. It cannot originate findings, record proof, approve candidates, apply fixes or publish audit evidence.

## v1 capabilities

### Deterministic audit engine

- pinned Gitleaks, Semgrep and OSV-Scanner execution;
- executable SHA-256 and reported-version verification;
- MobSF API upload, scan, JSON-report retrieval, cleanup and report normalization;
- immutable MobSF container-image pins and least-privilege container command generation;
- noninteractive execution, Semgrep telemetry disabled and OSV offline by default;
- deterministic multi-scanner SARIF 2.1.0 normalization and merge;
- stable SHA-256 finding identities and versioned severity mapping;
- redaction, staged-bus secret scanning, manifests, gates, size limits and atomic local publication;
- fail-closed bus states, cross-file validation, tamper detection, source-drift rejection and ownership checks.

### Audit publication

- disconnected canonical branch `assay/audit`;
- first audit commit has no source-history parent;
- subsequent audit commits preserve only audit history;
- explicit compare-and-swap `--force-with-lease` publication;
- remote ref verification and stale-lease rejection;
- no worktree checkout or source-branch mutation required for publication.

### Remediation candidates

- immutable candidate identity bound to source, finding, patch and proving test;
- dedicated `assay/fix/<candidate-id>` branches;
- strict JSON persistence, per-candidate locks and optimistic revisions;
- append-only hash-chained lifecycle events;
- exact Git patch, branch, ancestry and clean-worktree checks;
- repeated fail-before/pass-after proof and scanner replay requirements;
- human-only approval and rejection;
- deterministic executor-only application records;
- stale-source transitions instead of silent application;
- no automatic merge path.

### Product and constellation surfaces

- read-only console snapshot export;
- standalone Android review console under `android-console/`;
- Fonebrew proposal gateway constrained to existing verified findings;
- Orrery health/status projection;
- optional read-only ASOM explanation boundary;
- executable Dell/self-hosted runner preflight and systemd hardening package.

## Product definition and v2 direction

The repository includes a product and experience layer in addition to the implementation contracts:

- a product promise, object model, language and scope;
- provisional personas grounded in the current workflows;
- human role and machine-actor authority boundaries;
- current v1 and target v2 information architecture;
- end-to-end main, failure and recovery journeys;
- a phased v2 roadmap beginning with deployment certification and multi-project visibility.

These documents are product hypotheses until validated through user research. They do not override the normative security and lifecycle contracts.

## Verification

```bash
./scripts/test.sh
# or
gradle check --no-daemon
```

The JVM check runs the core, candidate, persistence, Git-worktree, remote-audit, MobSF, integration and runner-preflight acceptance suites. Separate GitHub workflows exercise real Gitleaks/Semgrep/OSV binaries and build/lint the Android console APK.

See `docs/VERIFICATION.md` for the mechanical evidence and the remaining environment-certification boundary.

## Common CLI flows

### Run a pinned local scanner

```bash
assay run-scanner \
  --scanner gitleaks \
  --tool /opt/assay/tools/gitleaks \
  --tool-lock /opt/assay/tool-lock.json \
  --target /work/source \
  --raw-output /work/evidence/gitleaks.sarif \
  --canonical-output /work/evidence/gitleaks.canonical.sarif
```

### Run MobSF

```bash
assay run-mobsf \
  --base-uri http://127.0.0.1:8000/api/v1 \
  --api-key-file /var/lib/assay/mobsf/api-key \
  --application /work/app.apk \
  --source-root /work/source \
  --version <version> \
  --image <repository>@sha256:<digest> \
  --raw-output /work/evidence/mobsf.json \
  --canonical-output /work/evidence/mobsf.sarif
```

### Publish the audit bus

```bash
assay publish-bus-git \
  --repo /work/source \
  --bus /work/audit \
  --source-commit <source-sha> \
  --expected-remote absent \
  --remote origin
```

Use the last observed `assay/audit` commit instead of `absent` for subsequent publications.

### Candidate lifecycle

```bash
assay candidate-create \
  --store /var/lib/assay/candidates \
  --source-repo owner/repo \
  --source-commit <source-sha> \
  --finding-fingerprint <sha256> \
  --proving-id <pt1-id> \
  --patch-digest <sha256> \
  --test-digest <sha256>

assay candidate-propose \
  --store /var/lib/assay/candidates \
  --candidate <candidate-id> \
  --revision 1 \
  --actor system:assay
```

The remaining `candidate-prepare`, `candidate-proof`, `candidate-approve`, `candidate-reject`, `candidate-apply` and `candidate-stale` commands are documented in `docs/CANDIDATE-LIFECYCLE.md`.

### Console and constellation projections

```bash
assay console-snapshot \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/console.json

assay orrery-status \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/orrery.json
```

### Runner preflight

```bash
assay runner-preflight \
  --work-root /var/lib/assay/work \
  --tool-lock /opt/assay/tool-lock.json \
  --gitleaks /opt/assay/tools/gitleaks \
  --semgrep /opt/assay/tools/semgrep \
  --osv /opt/assay/tools/osv-scanner \
  --git /usr/bin/git \
  --container-runtime /usr/bin/podman
```

## Documentation

### Product and experience

- `docs/PRODUCT-EXPERIENCE.md` — product promise, object model, principles, scope and language
- `docs/PERSONAS-AND-ROLES.md` — provisional personas, permissions and machine actors
- `docs/INFORMATION-ARCHITECTURE.md` — current v1 and target v2 IA
- `docs/USER-JOURNEYS.md` — main, failure and recovery journeys
- `docs/V2-ROADMAP.md` — deployment certification, v2 phases and v3 horizon

### Trust, implementation and operations

- `docs/CONTRACT.md` — normative audit-bus and trust contract
- `docs/THREAT-MODEL.md` — threats and fail-closed controls
- `docs/TOOLCHAIN.md` — scanner pins and execution assumptions
- `docs/CANDIDATE-LIFECYCLE.md` — remediation state machine and CLI
- `docs/INTEGRATIONS.md` — Fonebrew, Orrery and ASOM boundaries
- `docs/RUNNER-OPERATIONS.md` — Dell/self-hosted runner deployment
- `docs/ANDROID-CONSOLE.md` — APK, snapshot and review workflow
- `docs/VERIFICATION.md` — verified versus environment-certified capabilities

## Certification boundary

The repository can mechanically verify the implementation against local Git remotes, fake MobSF endpoints, real scanner binaries and an Android build environment. It cannot truthfully certify hardware or connected services it cannot access. Final deployment certification therefore requires the actual Dell runner, pinned MobSF image and APK, live repository remote, and installed Fonebrew/Orrery/optional ASOM environments.

Status ceiling: `ready-for-human-review`. Nothing auto-merges.
