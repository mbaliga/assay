# Candidate lifecycle

Assay turns a deterministic finding into a separately identified remediation candidate. A candidate never replaces or edits the finding that caused it.

## Identity

A candidate ID is derived from:

- source repository and exact source commit;
- deterministic finding fingerprint;
- patch digest;
- proving-test digest.

The resulting branch is fixed as `assay/fix/<candidate-id>`. Identity fields cannot change after creation.

## States

The supported path is:

```text
DETECTED -> PROPOSED -> PROOF_PASSED -> APPROVED -> APPLIED
                          |                |
                          +-> REJECTED     +-> STALE
```

Additional terminal or recovery states are defined by the contract for failed proof, withdrawal, supersession and obsolescence. Every transition is checked by the lifecycle engine.

## Authority boundaries

- `system:assay` creates a candidate.
- `system:fonebrew` may propose a candidate only against an existing verified finding.
- `system:proof-runner` is the only actor that may record proof.
- `human:<identity>` is required to approve or reject a proof-passed candidate.
- `system:executor` is the only actor that may record application.
- `system:source-watch` records source drift as `STALE`.

AI and integrations cannot create findings, record proof, approve, reject, or apply candidates.

## Persistence

Candidate records are strict JSON documents stored as `<candidate-id>.json`. The store provides:

- exact-key decoding and schema-version checks;
- full event-chain and lifecycle validation on every read;
- per-candidate process locks;
- optimistic revision checks;
- immutable identity enforcement;
- atomic replacement with file and directory sync;
- path confinement, no-follow reads and symlink rejection;
- private owner-only permissions on POSIX filesystems.

A stale writer cannot overwrite a newer revision.

## Proof binding

A proof document must bind to the same:

- proving ID;
- finding fingerprint;
- source commit;
- patch digest;
- proving-test digest.

The proof gate requires repeated assertion failure before the fix, repeated passing execution after the fix, scanner presence before and clearance after, and non-identical before/after evidence. The Git worktree must be on the dedicated candidate branch, clean, descended from the audited source commit, and contain the exact candidate patch.

## CLI sequence

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

assay candidate-prepare \
  --store /var/lib/assay/candidates \
  --candidate <candidate-id> \
  --repo /work/source \
  --patch /work/candidate.patch

# Commit the staged patch on assay/fix/<candidate-id>, then produce proof.json.

assay candidate-proof \
  --store /var/lib/assay/candidates \
  --candidate <candidate-id> \
  --revision 2 \
  --repo /work/source \
  --proof /work/proof.json

assay candidate-approve \
  --store /var/lib/assay/candidates \
  --candidate <candidate-id> \
  --revision 3 \
  --actor human:reviewer@example.com

assay candidate-apply \
  --store /var/lib/assay/candidates \
  --candidate <candidate-id> \
  --revision 4 \
  --repo /work/source
```

`candidate-apply` records that the approved fix commit is valid. It does not merge a branch and does not bypass repository review.
