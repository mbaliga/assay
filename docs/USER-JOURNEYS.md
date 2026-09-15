# User journeys

This document describes the end-to-end journeys supported by v1/v1.1 and the target improvements for v2. It is product documentation, not an alternative lifecycle contract. When this document and the normative contract differ, the contract wins.

## Journey map summary

| Journey | Primary persona | Entry condition | Desired outcome |
|---|---|---|---|
| Understand Assay on first launch | Android maintainer or evaluator | App installed; no runner configured | User understands the three paths and their trust levels |
| Quick-check an APK | Android maintainer or release tester | APK available on device | Immediate offline package observations with clear limitations |
| Explore the guided sample | Any first-time user | No APK or snapshot required | User understands findings, candidates and proof/decision separation |
| Review runner-verified evidence | Maintainer or reviewer | Valid snapshot available | User understands provenance, risk and next action |
| Configure trusted execution | Runner operator | New or changed environment | Environment passes preflight and is eligible to produce evidence |
| Audit a source revision | Android maintainer | Exact source commit selected | Valid multi-scanner evidence is published or a precise blocking state is returned |
| Propose remediation | Maintainer or assisted remediator | Existing verified finding | Candidate is created without altering the finding |
| Prove remediation | Proof runner with operator support | Candidate branch and exact patch/test exist | Proof is recorded against the candidate or fails closed |
| Decide | Security reviewer | Candidate is proof-passed | Attributable approve or reject decision is recorded |
| Apply and hand off | Maintainer and executor | Candidate is approved and source remains valid | Application is recorded and normal repository review continues |
| Handle source drift | Maintainer and source watch | Source context changes | Candidate becomes stale rather than silently actionable |
| Observe portfolio health | Portfolio observer | Orrery projection available | User identifies healthy, degraded, blocked and unknown projects |

## Journey 1: Understand Assay on first launch

### Persona

Android maintainer, release tester, security reviewer or evaluator.

### Trigger

The user installs Assay 1.1 and opens it without prior setup.

### Main path

1. Assay opens to a home screen, not an empty evidence state.
2. The user sees the product purpose in plain language.
3. The user sees three actions:
   - **Scan an APK** — immediate on-device package inspection;
   - **Open verified audit** — import trusted-runner evidence;
   - **Explore a sample project** — guided demonstration.
4. Supporting copy explains that the local quick check and runner-verified audit have different assurance levels.
5. The user chooses a path without needing to understand runner directories or JSON generation first.

### Failure paths

- A previous action failed: the home screen shows the bounded error and keeps all three paths available.
- The user expected a connected dashboard: copy explains that v1.1 is offline-first and the connected read model is a v2 capability.
- The user assumes local inspection is the full audit: trust labels and explanatory copy correct the assumption before results.

### Exit criteria

The user can answer:

- What can I do immediately?
- Which path requires a trusted runner?
- Which information is sample-only?

### v2 improvements

- Connected read-only project list after authentication.
- First-run preference between local inspection and connected project mode.
- Contextual setup guidance based on role.
- Optional onboarding that can be skipped and reopened.

## Journey 2: Quick-check an APK on the device

### Persona

Android maintainer or release tester.

### Trigger

The user has an APK on the device and wants an immediate package-level review.

### Main path

1. User selects **Scan an APK**.
2. Android's system document picker opens with APK-compatible types.
3. User grants access only to the selected file.
4. Assay copies the APK into a bounded private temporary file.
5. Analysis runs off the main thread.
6. Assay reads package, component, permission, signing and archive metadata.
7. The temporary APK is deleted in a `finally` path whether inspection passes or fails.
8. Assay shows a **Local quick check** trust banner.
9. User reviews:
   - app label and package name;
   - version and SDK range;
   - file size and SHA-256;
   - signer, permission, exported-component and native-library counts;
   - package-level observations ordered by severity.
10. User opens an observation to read explanation, evidence and local rule ID.
11. User can scan another APK or move to **Open verified audit**.

### Current checks

- debuggable build;
- test-only build;
- backup enabled;
- cleartext traffic allowed;
- exported components without guarding permissions;
- broad/special permissions;
- dangerous permissions;
- signing-certificate absence;
- native shared-library presence;
- package and SDK metadata.

### Failure and recovery paths

- User cancels picker: return to home with no error state.
- APK cannot be opened: show a precise bounded message and return to home.
- File is empty, malformed or not an APK: reject; never render a clean result.
- File exceeds 1 GB: stop before unbounded copying.
- Package metadata cannot be parsed: report parse failure; delete temporary file.
- Android version uses an older package API: use the guarded compatible path.
- Inspection is interrupted: temporary file cleanup still runs.

### Exit criteria

- The user receives useful package-level observations without uploading the APK.
- The report is never labelled runner-verified.
- No candidate or proof workflow is created from local observations.

### v2 improvements

- Export or share a signed local report with explicit local-assurance metadata.
- Rule explanations and links to Android guidance.
- Compare two APK builds without treating comparison as source evidence.
- Certificate lineage and stronger signing-scheme inspection.
- Optional local SBOM/package inspection where platform APIs allow it safely.
- User-tested prioritization to reduce misleading package-level warnings.

## Journey 3: Explore the guided sample

### Persona

Any first-time user.

### Trigger

The user wants to understand Assay without selecting a file or configuring infrastructure.

### Main path

1. User selects **Explore a sample project**.
2. Assay displays a **Sample data** trust banner.
3. User sees representative repository/run information, findings and candidates.
4. User opens a sample finding to understand rule, severity, location, fingerprint and related candidate count.
5. User opens a proof-passed candidate to understand lifecycle, branch, revision and human-decision handoff.
6. The experience distinguishes proof passed from approved, applied, merged and released.
7. User selects **Try your APK** to move into the local quick-check journey.

### Failure paths

- Sample data is mistaken for a real audit: trust banner and sample identifiers remain visible.
- User copies a command from sample data: placeholders and sample context prevent representation as an executed decision.

### Exit criteria

The user understands the core object relationships and knows the sample has no evidentiary meaning.

### v2 improvements

- Short role-specific walkthroughs.
- Resettable guided tasks.
- Accessibility-tested explanatory annotations.

## Journey 4: Review runner-verified audit evidence

### Persona

Android maintainer or security reviewer.

### Trigger

A `console-snapshot` JSON projection is available from the trusted runner.

### Main path

1. User selects **Open verified audit**.
2. Android's document picker opens.
3. Assay reads at most 8 MB and parses the complete snapshot.
4. The app validates schema version, required and unknown fields, identifiers, relationships, availability state and record bounds.
5. Assay displays a **Runner-verified evidence** trust banner.
6. Evidence availability is shown before findings.
7. For ready evidence, user reviews repository, source commit, run ID, generation time and counts.
8. User opens finding details to inspect scanner, rule, severity, location, message, fingerprint and candidate coverage.
9. User opens candidate details to inspect lifecycle, revision, fix branch and approval visibility.
10. For proof-passed candidates, the app may copy an explicit approve/reject command template.
11. Decision execution remains on the trusted runner and is revalidated there.

### Failure and recovery paths

- Snapshot is too large: reject before parsing.
- JSON is malformed or contains unknown fields: reject the whole document.
- Identifiers or cross-references are invalid: reject rather than partially render.
- Evidence is unavailable or not configured: show reason and no findings.
- Evidence is stale or invalid: show blocking state; do not render it as ready.
- User opens an ordinary JSON file: show validation failure and return to home.

### Exit criteria

The user can answer:

- Is this runner evidence valid for the source I care about?
- Which findings are unresolved?
- Which findings have candidates?
- Which candidates require proof, review or follow-up?

### v2 improvements

- Persistent connected project/run navigation.
- Search, filter, sort and saved views.
- Full detail screens instead of dialogs.
- Run-to-run change views.
- Deep links from Orrery and notifications.
- Signed projection verification and freshness-aware local cache.

## Journey 5: Configure trusted execution

### Persona

Runner operator and platform steward.

### Trigger

A Dell/self-hosted runner is installed, a scanner is upgraded, the MobSF image changes, or runtime configuration is modified.

### Main path

1. Operator installs the pinned Assay CLI, scanners, Git and container runtime.
2. Credentials are placed outside audit artifact paths.
3. Operator configures private work, candidate and evidence directories.
4. Operator runs `assay runner-preflight` or the systemd preflight unit.
5. Assay verifies non-root execution, cgroup v2, directory permissions, capacity, executable digests, scanner versions, Git and container runtime.
6. Operator records deployment-specific evidence from the actual machine.
7. The environment becomes eligible to execute audits.

### Failure paths

- Tool digest or version mismatch: block and identify exact tool.
- Workspace permissions too broad: block and identify path/mode.
- Container runtime unavailable: MobSF remains unavailable, not clean.
- Insufficient storage: block before evidence generation.
- CI preflight passes but Dell preflight does not: deployment remains uncertified.

### Exit criteria

- Required checks pass on the real environment.
- Credentials are not present in artifacts.
- Cancellation and cleanup behavior has been exercised.

### v2 improvements

- Persistent environment registry with evidence digest and expiry.
- Drift detection between preflight and audit execution.
- Operator-facing remediation instructions and ownership routing.

## Journey 6: Audit an exact source revision

### Persona

Android maintainer, with execution performed by the trusted runner.

### Trigger

A maintainer requests an audit for a commit, release candidate or explicit source revision.

### Main path

1. Maintainer identifies repository and exact source commit.
2. Runner validates source ownership and worktree state.
3. Assay verifies scanner executables against the tool lock.
4. Gitleaks, Semgrep and OSV-Scanner execute with bounded, noninteractive settings.
5. MobSF executes when configured; upload, scan, report retrieval and cleanup are checked.
6. Assay redacts, normalizes and merges deterministic SARIF.
7. Cross-file contracts, manifests, source binding, sizes, paths and digests are validated.
8. A local evidence bus is published atomically.
9. Evidence is published to disconnected `assay/audit` history with compare-and-swap lease semantics.
10. Console and Orrery projections can be generated.

### User-visible outcomes

- Ready with zero findings.
- Ready with findings.
- Not configured.
- Unavailable.
- Invalid.
- Stale.

Only the first two are valid completed evidence.

### Failure paths

- Scanner absent or unsuccessful: unavailable or invalid, never zero findings.
- Path traversal, symlink or oversized evidence: reject publication.
- Source changed during execution: source binding fails.
- Remote audit branch moved: stale lease failure.
- MobSF cleanup fails: failure remains visible.

### Exit criteria

A verified bus exists for the exact commit, or a precise blocked state is recorded.

### v2 improvements

- Project/run registry.
- Scheduled and event-triggered audits.
- Notifications for new high-severity findings and blocked runs.
- Compatible run comparison and baselines.

## Journey 7: Propose a remediation candidate

### Persona

Android maintainer or assisted remediator using Fonebrew.

### Trigger

A runner-verified finding is selected for remediation.

### Manual path

1. Maintainer creates a patch and proving test.
2. Digests are calculated.
3. Assay creates a candidate bound to repository, source commit, finding fingerprint, patch digest and proving-test digest.
4. Assay assigns immutable candidate ID and `assay/fix/<candidate-id>` branch.
5. Candidate transitions from detected to proposed.

### Fonebrew-assisted path

1. User asks Fonebrew to explain or remediate an existing verified finding.
2. Fonebrew produces patch and test artifacts.
3. Gateway verifies the finding and source commit.
4. Candidate is created and proposed as `system:fonebrew`.
5. Fonebrew cannot perform proof, decision or application transitions.

### Failure paths

- Local quick-check observation supplied as a finding: reject; it has no runner fingerprint contract.
- Unknown finding fingerprint: reject.
- Source commit stale: reject or mark stale.
- Patch/test digest changes: create a new candidate identity.
- AI invents an issue absent from evidence: gateway rejects it.

### Exit criteria

Candidate identity is immutable and the original finding remains unchanged.

## Journey 8: Prepare and prove a candidate

### Persona

Maintainer, proof runner and runner operator.

### Main path

1. Assay prepares or verifies the dedicated candidate branch.
2. Patch is applied and committed.
3. Proof runner verifies branch, clean worktree, source ancestry and exact patch.
4. Proving assertion fails repeatedly before the fix.
5. It passes repeatedly after the fix.
6. Relevant scanner evidence exists before and is cleared after.
7. Before/after evidence is non-identical and candidate-bound.
8. Assay records proof as `system:proof-runner`.
9. Candidate transitions to proof-passed.

### Failure paths

- Test does not fail before fix.
- Test is flaky.
- Scanner finding persists.
- Branch or patch mismatches identity.
- Worktree contains unrelated changes.
- Source ancestry no longer holds.

All fail closed; source drift marks the candidate stale.

## Journey 9: Approve or reject

### Persona

Security reviewer and approver.

### Main path

1. Reviewer inspects source, finding, candidate identity, patch/test digests and proof.
2. Reviewer confirms revision and source validity.
3. Reviewer executes approve or reject using `human:<identity>`.
4. Assay verifies state, revision and actor class.
5. Decision is appended to the hash-chained record.
6. Candidate becomes approved or rejected.

Copying a command in Android is not a decision.

### Failure paths

- Non-human actor attempts decision.
- Candidate revision changed.
- Source drift occurred.
- Proof absent or invalid.

## Journey 10: Apply and continue repository review

### Persona

Maintainer with deterministic executor.

### Main path

1. Executor revalidates approval, source, branch, fix commit, patch and ancestry.
2. Assay records application as `system:executor`.
3. Candidate becomes applied.
4. Maintainer continues the repository's normal PR and review process.
5. Repository governance decides merge and release.

`APPLIED` does not mean merged, deployed, released or risk-free.

## Journey 11: Handle source drift

### Persona

Maintainer and source-watch actor.

### Main path

1. Assay detects that current source no longer matches candidate binding.
2. `system:source-watch` records drift.
3. Candidate becomes stale.
4. Approval/application actions are removed or disabled.
5. Maintainer may reproduce the proposal against a new revision.
6. Changed identity inputs create a new candidate.

No old decision is reused silently.

## Journey 12: Observe portfolio health

### Persona

Portfolio observer using Orrery.

### Main path

1. Orrery validates the Assay projection.
2. Project is shown as:
   - `healthy`: valid evidence and no unresolved findings;
   - `degraded`: valid evidence with unresolved findings;
   - `blocked`: invalid or stale evidence;
   - `unknown`: not configured or unavailable.
3. Observer prioritizes maintainer or operator attention.
4. Observer follows a read-only handoff to Assay.

The observer can distinguish security work from operational evidence failure.

## Cross-journey UX requirements

- Trust tier is visible on every Android result surface.
- Local quick-check observations never enter runner finding or candidate language.
- Repository and source commit are shown on runner evidence, candidate and decision surfaces.
- Zero findings is never shown for unavailable or invalid evidence.
- Actions are state-specific and explain why they are unavailable.
- Actor identity and timestamp are preserved in timelines.
- AI explanations are advisory and non-evidentiary.
- Merge and deployment remain outside Assay's application language.
- Stable identifiers are copyable for support and audit.
- Every failure has an owner: user, maintainer, reviewer, operator or integration.

## Journey instrumentation for v2

Measure without treating speed as the only success criterion:

- first-launch path selected;
- local APK inspection completion and failure reasons;
- percentage of users who correctly distinguish local and runner assurance in usability testing;
- movement from sample exploration to a real APK scan;
- time from valid runner finding to first candidate;
- time from proposal to proof result;
- time awaiting human decision;
- percentage of candidates invalidated by source drift;
- proof failure reasons and recurrence;
- blocked audits by root cause;
- projects with fresh valid evidence;
- reviewer rework caused by missing context;
- ratio of AI proposals entering proof, not merely generated.