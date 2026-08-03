# User journeys

This document describes the end-to-end journeys supported by v1 and the target improvements for v2. It is product documentation, not an alternative lifecycle contract. When this document and the normative contract differ, the contract wins.

## Journey map summary

| Journey | Primary persona | Entry condition | Desired outcome |
|---|---|---|---|
| Configure trusted execution | Runner operator | New or changed environment | Environment passes preflight and is eligible to produce evidence |
| Audit a source revision | Android maintainer | Exact source commit selected | Valid multi-scanner evidence is published or a precise blocking state is returned |
| Review evidence | Maintainer or reviewer | Ready snapshot or verified bus | User understands risk, provenance and next action |
| Propose remediation | Maintainer or assisted remediator | Existing verified finding | Candidate is created without altering the finding |
| Prove remediation | Proof runner with operator support | Candidate branch and exact patch/test exist | Proof is recorded against the candidate or fails closed |
| Decide | Security reviewer | Candidate is proof-passed | Attributable approve or reject decision is recorded |
| Apply and hand off | Maintainer and executor | Candidate is approved and source is still valid | Application is recorded and normal repository review continues |
| Handle source drift | Maintainer and source watch | Source context changes | Candidate becomes stale rather than silently remaining actionable |
| Observe portfolio health | Portfolio observer | Orrery projection available | User identifies healthy, degraded, blocked and unknown projects |

## Journey 1: Configure trusted execution

### Persona

Runner operator and platform steward.

### Trigger

A new Dell/self-hosted runner is installed, a scanner is upgraded, the MobSF image changes, or the runtime configuration is modified.

### Main path

1. Operator installs the pinned Assay CLI, scanners, Git and container runtime.
2. Operator places secrets and credentials outside the audit artifact path.
3. Operator configures private work, candidate and evidence directories.
4. Operator runs `assay runner-preflight` or the systemd preflight unit.
5. Assay verifies non-root execution, cgroup v2, directory permissions, available capacity, exact executable digests, scanner versions, Git and the container runtime.
6. Operator records deployment-specific evidence from the actual runner.
7. The environment becomes eligible to execute audits.

### Failure paths

- Tool digest or version mismatch: block and identify the exact tool.
- Workspace permissions are too broad: block and provide the offending path/mode.
- Container runtime unavailable: MobSF path remains unavailable, not clean.
- Insufficient storage: block before evidence generation.
- Preflight passes in CI but not on the Dell machine: deployment remains uncertified.

### Exit criteria

- All required preflight checks pass on the actual environment.
- Credentials are not present in generated artifacts.
- Cancellation and cleanup behavior has been exercised.

### v2 improvements

- Persistent environment registry with certification timestamp and evidence digest.
- Drift detection between successful preflight and audit execution.
- Operator-facing remediation instructions and ownership routing.
- Expiring certification rather than a permanent boolean.

## Journey 2: Audit an exact source revision

### Persona

Android maintainer, with execution performed by the trusted runner.

### Trigger

A maintainer requests an audit for a commit, release candidate or explicitly selected source revision.

### Main path

1. Maintainer identifies repository and exact source commit.
2. Runner validates source ownership and worktree state.
3. Assay verifies scanner executables against the tool lock.
4. Gitleaks, Semgrep and OSV-Scanner execute with bounded, noninteractive settings.
5. MobSF executes when configured; upload, scan, report retrieval and cleanup are all checked.
6. Assay redacts, normalizes and merges findings into deterministic SARIF.
7. Cross-file contracts, manifests, source binding, sizes, paths and digests are validated.
8. A local evidence bus is published atomically.
9. The evidence is published to disconnected `assay/audit` history using compare-and-swap lease semantics.
10. A console and Orrery projection can be generated.

### Expected user-visible outcomes

- Ready with zero findings.
- Ready with one or more findings.
- Not configured.
- Unavailable.
- Invalid.
- Stale.

Only the first two represent valid completed evidence.

### Failure paths

- Scanner is absent or exits unsuccessfully: audit is unavailable or invalid, never zero findings.
- Evidence contains path traversal, symlink or oversized content: publication is rejected.
- Source changed during execution: source binding fails.
- Remote audit branch moved: publication fails with a stale lease.
- MobSF cleanup fails: failure remains visible and must not be silently downgraded.

### Exit criteria

- A verified bus exists for the exact source commit, or a precise blocked state is recorded.
- The audit publication can be independently verified.

### v2 improvements

- Project and run registry.
- Scheduled and event-triggered audits.
- Incremental run support without weakening full-run provenance.
- Notifications for newly introduced high-severity findings and blocked runs.
- Run comparison against a compatible baseline.

## Journey 3: Review audit evidence

### Persona

Android maintainer or security reviewer.

### Trigger

A console snapshot, CLI output or project/run view is available.

### v1 main path

1. User opens the Android console.
2. User selects a verified snapshot using the system document picker.
3. The app validates schema, size, identifiers, relationships and availability rules.
4. The app shows evidence status before any findings.
5. For ready evidence, the user reviews repository, source commit, run ID, time and counts.
6. User opens finding details to inspect scanner, rule, severity, location, message, fingerprint and candidate coverage.
7. User opens candidate details to inspect lifecycle, revision, branch and approval visibility.

### Failure paths

- Snapshot is too large, malformed or contains unknown fields: reject and show invalid state.
- Snapshot is unavailable or not configured: show reason and no findings.
- Snapshot is ready but references are inconsistent: reject rather than partially render.

### Exit criteria

The user can answer:

- Is this evidence valid for the source I care about?
- Which findings are unresolved?
- Which findings have remediation candidates?
- Which candidates require proof, review or follow-up?

### v2 improvements

- Persistent project/run navigation.
- Search, filter, sort and saved views.
- Full finding and candidate detail screens instead of dialogs.
- Run-to-run change views.
- Deep links from Orrery and notifications.
- Accessible evidence summaries with raw locator links.

## Journey 4: Propose a remediation candidate

### Persona

Android maintainer or assisted remediator using Fonebrew.

### Trigger

A verified finding has been selected for remediation.

### Manual main path

1. Maintainer creates a patch and proving test.
2. Digests are calculated for the patch and test.
3. Assay creates a candidate bound to repository, source commit, finding fingerprint, patch digest and proving-test digest.
4. Assay assigns the immutable candidate ID and dedicated `assay/fix/<candidate-id>` branch.
5. Candidate transitions from detected to proposed.

### Fonebrew-assisted main path

1. User asks Fonebrew to explain or remediate an existing verified finding.
2. Fonebrew produces patch and test artifacts.
3. The gateway verifies the finding exists and the source commit remains current.
4. Candidate is created and proposed as `system:fonebrew`.
5. Fonebrew cannot perform later proof, decision or application transitions.

### Failure paths

- Unknown finding fingerprint: reject.
- Source commit is stale: reject or mark stale.
- Patch or test digest changes after identity creation: create a new candidate; do not mutate identity.
- AI produces a plausible issue not present in evidence: no candidate may be created through the Fonebrew gateway.

### Exit criteria

- Candidate identity is immutable and inspectable.
- The original finding remains unchanged.
- Patch and proving test are available for branch preparation.

### v2 improvements

- Guided candidate creation with artifact preview.
- Multiple proposals for the same finding with clear comparison.
- Candidate supersession and withdrawal UX.
- Repository-host PR draft creation after proof and approval, while preserving normal review governance.

## Journey 5: Prepare and prove a candidate

### Persona

Maintainer, proof runner and runner operator.

### Trigger

A proposed candidate has an exact patch and proving test.

### Main path

1. Assay prepares or verifies the dedicated candidate branch.
2. Patch is applied and committed on `assay/fix/<candidate-id>`.
3. Proof runner verifies branch name, clean worktree, source ancestry and exact patch.
4. Proving assertion fails repeatedly before the fix.
5. Proving assertion passes repeatedly after the fix.
6. Relevant scanner evidence exists before and is cleared after.
7. Before/after evidence is non-identical and bound to the candidate.
8. Assay records proof as `system:proof-runner`.
9. Candidate transitions to proof-passed.

### Failure paths

- Test does not fail before fix: proof fails.
- Test is flaky: proof fails until repetition requirement passes.
- Scanner finding persists: proof fails.
- Branch or patch does not match candidate identity: reject proof.
- Worktree contains unrelated changes: reject proof.
- Source ancestry no longer holds: mark stale.

### Exit criteria

- Proof is mechanically bound to the exact candidate and fix commit.
- Candidate is eligible for human review, not automatically approved.

### v2 improvements

- Isolated disposable proof workers.
- Proof artifact viewer and timeline.
- Flakiness diagnostics.
- Configurable proving policies per rule or project.
- Queue and capacity management for proof execution.

## Journey 6: Approve or reject

### Persona

Security reviewer and approver.

### Trigger

A candidate is proof-passed.

### v1 main path

1. Reviewer inspects source, finding, candidate identity, patch/test digests and proof.
2. Reviewer confirms current revision and source validity.
3. Reviewer executes explicit approve or reject command using `human:<identity>`.
4. Assay verifies the candidate state, expected revision and actor class.
5. Decision is appended to the hash-chained event record.
6. Candidate transitions to approved or rejected.

The Android console may copy a command template. Copying it is not a decision; execution on the trusted surface is required.

### Failure paths

- Non-human actor attempts decision: reject.
- Candidate revision changed: reject stale decision.
- Source drift occurred: candidate becomes stale.
- Proof is absent or invalid: approval is unavailable.

### Exit criteria

- Decision is attributable and bound to the exact proof and revision.
- Rejected candidates cannot silently continue toward application.

### v2 improvements

- Dedicated authenticated decision queue.
- Strong identity integration and reviewer authorization policy.
- Decision rationale and structured residual-risk acknowledgement.
- Two-person or policy-based approval for selected severity classes.
- Replay-resistant mobile approval only after threat-model revision.

## Journey 7: Apply and continue repository review

### Persona

Maintainer with the deterministic executor operating the record transition.

### Trigger

Candidate is approved, source binding remains valid and the fix commit is present on the expected branch.

### Main path

1. Executor revalidates approval, source commit, branch, fix commit, patch and ancestry.
2. Assay records application as `system:executor`.
3. Candidate transitions to applied.
4. Maintainer opens or continues the repository's normal pull request and review process.
5. Repository governance decides whether and when to merge and release.

### Critical distinction

`APPLIED` means the approved fix commit satisfies the Assay candidate contract. It does not mean merged, deployed, released or risk-free.

### Failure paths

- Source commit or branch has drifted: mark stale.
- Fix commit is not descended from audited source: block.
- Approval references a previous revision: block.
- Worktree or patch does not match: block.

### Exit criteria

- Application record is complete and inspectable.
- No Assay path bypasses the repository's normal merge controls.

### v2 improvements

- Create or update a draft PR with evidence links.
- Repository-host status checks for candidate proof and approval.
- Post-merge observation that creates a new audit run rather than rewriting application state.

## Journey 8: Handle source drift

### Persona

Maintainer and source-watch actor.

### Trigger

The source revision, candidate base or required context changes before decision or application.

### Main path

1. Assay detects that current source no longer matches candidate binding.
2. `system:source-watch` records the drift.
3. Candidate transitions to stale.
4. UI removes or disables approval/application actions.
5. Maintainer chooses whether to reproduce the proposal against a new source revision.
6. A new candidate is created when identity inputs change.

### Exit criteria

No old decision is silently reused for new source context.

### v2 improvements

- Explain which files or commits invalidated the candidate.
- Offer a guided rebase/re-prove flow that creates a new candidate identity.
- Link superseded and replacement candidates.

## Journey 9: Observe portfolio health

### Persona

Portfolio observer using Orrery.

### Trigger

Orrery receives an Assay health projection.

### Main path

1. Orrery validates and reads the projection.
2. Project is shown as:
   - `healthy`: valid evidence and no unresolved findings;
   - `degraded`: valid evidence with unresolved findings;
   - `blocked`: invalid or stale evidence;
   - `unknown`: not configured or unavailable.
3. Observer prioritizes projects requiring maintainer or operator attention.
4. Observer follows a read-only link or handoff to Assay for detail.

### Exit criteria

The observer can distinguish security work from operational evidence failure.

### v2 improvements

- Portfolio trends and ageing.
- Explicit ownership and escalation routing.
- Evidence freshness policy.
- Drill-down that preserves Assay's read-only boundary.

## Cross-journey UX requirements

- Always show repository and source commit on detail and decision surfaces.
- Never show zero findings for unavailable or invalid evidence.
- Use state-specific actions; hide or disable actions that cannot legally execute.
- Explain why an action is unavailable.
- Preserve actor identity and timestamp in timelines.
- Label AI explanations as advisory and non-evidentiary.
- Keep merge and deployment outside Assay's application language.
- Provide copyable stable identifiers for support and audit.
- Ensure every failure has an owner: maintainer, reviewer, operator or integration.

## Journey instrumentation for v2

Measure without treating speed as the only success criterion:

- time from valid finding to first candidate;
- time from candidate proposal to proof result;
- time waiting for human decision;
- percentage of candidates invalidated by source drift;
- proof failure reasons and recurrence;
- blocked audits by root cause;
- percentage of projects with fresh valid evidence;
- false-positive/accepted-risk volume once triage exists;
- reviewer rework caused by missing context;
- ratio of AI proposals accepted for proof, not merely generated.