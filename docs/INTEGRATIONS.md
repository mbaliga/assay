# Constellation integrations

Assay remains independently useful. Fonebrew, Orrery and ASOM integrate through narrow projections and capability boundaries rather than sharing internal mutable state.

## Console snapshot

`console-snapshot` derives a read-only JSON projection from a verified audit bus and the candidate store.

```bash
assay console-snapshot \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/console.json
```

The projection contains run identity, findings, candidate lifecycle and approval visibility. It does not contain an API token or an executable action. Missing, invalid and stale evidence remain distinct from a valid run containing zero findings.

## Fonebrew

Fonebrew has one core write capability: propose a patch/test pair for an existing verified finding.

```bash
assay fonebrew-propose \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --source-commit <source-sha> \
  --finding-fingerprint <sha256> \
  --patch-digest <sha256> \
  --test-digest <sha256>
```

The gateway rejects unknown findings and stale source commits. It creates and proposes a candidate as `system:fonebrew`; it cannot record proof, approve, reject or apply it. Studio may make transport and handoff seamless, but the trust boundary does not change. A non-Studio workflow may pass the same digests and artifacts manually.

## Orrery

`orrery-status` produces a small health projection:

```bash
assay orrery-status \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/orrery.json
```

Health values are:

- `unknown`: not configured or temporarily unavailable;
- `blocked`: invalid or stale evidence;
- `degraded`: verified unresolved findings exist;
- `healthy`: verified evidence has no unresolved findings.

Orrery receives status only. It does not gain candidate mutation authority.

## ASOM

ASOM is optional and advisory. The core defines a read-only explanation request and response type. Model output may explain a deterministic finding, but it is not evidence and cannot alter the finding, proof gate, severity, approval or application record.

## Transport responsibility

The repository implements validated files and CLI boundaries. Deployment-specific authentication, message transport and service discovery belong to the corresponding installed environments. Those adapters must preserve:

- exact source-commit binding;
- complete JSON documents rather than partial mutable records;
- no credentials inside audit artifacts;
- explicit failure states;
- the actor restrictions in `docs/CANDIDATE-LIFECYCLE.md`.
