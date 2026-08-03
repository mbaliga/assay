# Information architecture

This document defines how Assay's information is grouped, related and presented across the Android app, CLI and future connected surfaces.

The IA follows the trust model. It must not collapse local package inspection, runner evidence, remediation, human decisions and operational state into a generic issue object.

## IA principles

1. **Trust level is visible before results.** Local quick check, runner-verified evidence and sample data are distinct contexts.
2. **Source context is always visible for runner evidence.** Every run, finding and candidate is understood against an exact repository and source commit.
3. **Evidence state precedes runner findings.** Users first learn whether evidence is ready, unavailable, invalid, stale or not configured.
4. **Local APK observations and runner findings remain separate.** A package-level quick-check result must not enter the runner finding or candidate hierarchy.
5. **Findings and candidates remain separate.** A finding is immutable scanner evidence; a candidate is a proposed remediation.
6. **Proof and approval are separate stages.** A passed proof is not a human decision, and approval is not application or merge.
7. **Operational diagnostics are available without polluting review.** Maintainers see a concise state; operators can inspect deeper causes.
8. **Authority is reflected in navigation and actions.** Read-only or local-inspection surfaces do not imply runner or decision authority.
9. **State labels use contract language.** UI copy does not invent softer synonyms for blocked or invalid states.

## System-level object model

```text
Local Android inspection
  └── Selected APK
        └── APK quick-check report
              ├── Package overview
              └── Local observations

Runner-verified system
  └── Project / repository
        └── Source revision
              ├── Audit run
              │     ├── Scanner executions
              │     ├── Evidence state and gate
              │     ├── Findings
              │     └── Audit publication
              │
              └── Finding
                    └── Remediation candidate
                          ├── Patch and proving test
                          ├── Candidate branch and fix commit
                          ├── Proof
                          ├── Human decision
                          └── Application record
```

Cross-cutting objects:

- Tool and container pins
- Severity and gate policy
- Runner/environment certification
- Integration projections
- Event and audit history
- Trust labels and assurance tier

## Current v1 / v1.1 IA

### Android app IA

The Android app is a shallow, offline-first surface with one home screen and three clearly labelled entry paths.

```text
Assay home
  ├── Scan an APK
  │     ├── Android document picker
  │     ├── Inspecting APK
  │     └── Local quick-check report
  │           ├── Trust banner: Local quick check
  │           ├── Package overview
  │           │     ├── App label and package
  │           │     ├── File name
  │           │     ├── Version
  │           │     ├── SDK range
  │           │     ├── APK size
  │           │     ├── Signer count
  │           │     ├── Permission count
  │           │     ├── Exported component count
  │           │     ├── Native-library count
  │           │     └── SHA-256
  │           ├── Finding metrics
  │           │     ├── Total observations
  │           │     ├── Actionable observations
  │           │     └── High-risk observations
  │           ├── Local observation cards
  │           │     └── Observation detail dialog
  │           │           ├── Title and severity
  │           │           ├── Explanation
  │           │           ├── Evidence
  │           │           └── Local rule ID
  │           └── Next actions
  │                 ├── Scan another APK
  │                 └── Open verified audit
  │
  ├── Open verified audit
  │     ├── Android document picker
  │     ├── Validating snapshot
  │     ├── Rejected snapshot
  │     │     └── Exact validation reason
  │     └── Runner-verified evidence
  │           ├── Trust banner: Runner-verified evidence
  │           ├── Evidence availability
  │           │     ├── Ready
  │           │     ├── Not configured
  │           │     ├── Unavailable
  │           │     └── Invalid / stale reason
  │           └── Ready snapshot
  │                 ├── Overview
  │                 │     ├── Repository
  │                 │     ├── Source commit
  │                 │     ├── Run ID
  │                 │     ├── Generated time
  │                 │     ├── Finding count
  │                 │     ├── Unresolved count
  │                 │     └── Candidate count
  │                 ├── Findings
  │                 │     └── Finding detail dialog
  │                 │           ├── Rule and message
  │                 │           ├── Scanner and severity
  │                 │           ├── Location
  │                 │           ├── Fingerprint
  │                 │           └── Related candidate count
  │                 └── Candidates
  │                       └── Candidate detail dialog
  │                             ├── Lifecycle
  │                             ├── Revision
  │                             ├── Fix branch
  │                             ├── Human approval visibility
  │                             ├── Finding fingerprint
  │                             └── Command handoff when eligible
  │
  └── Explore a sample project
        └── Sample project
              ├── Trust banner: Sample data
              ├── Sample overview
              ├── Sample findings
              ├── Sample candidates
              └── Try your APK
```

Global Android behaviors:

- Every detail state has a visible path back to Assay home.
- Local, verified and sample trust labels remain visible above results.
- The app does not persist a project list in v1.1.
- Local reports and imported snapshots are held in memory.
- No Internet permission is requested.
- Approval/rejection is a command handoff, not an in-app lifecycle mutation.

### CLI command domains

The CLI is understood as six command families, even though commands remain flat for shell usability:

1. **Audit execution**
   - run pinned scanner
   - run MobSF
   - normalize and validate evidence
2. **Audit publication**
   - publish local bus
   - publish disconnected `assay/audit` history
3. **Candidate lifecycle**
   - create
   - list and show
   - propose
   - prepare branch
   - record proof
   - approve or reject
   - apply
   - mark stale
4. **Review projections**
   - console snapshot
   - Orrery status
5. **Constellation handoff**
   - Fonebrew proposal
   - ASOM explanation types
6. **Operations**
   - runner preflight
   - tool and environment diagnostics

Documentation and future help output should group commands this way rather than presenting an undifferentiated alphabetical list.

## Content hierarchy rules

### Local APK report

Present information in this order:

1. Trust label: Local quick check
2. APK/app identity
3. Package and signing overview
4. High-risk observations
5. Remaining observations
6. SHA-256 and technical metadata
7. Path to a runner-verified audit

A local report must not show repository, source commit, proof or candidate controls because those objects do not exist in this context.

### Runner-verified project or snapshot

Present information in this order:

1. Evidence availability and blocking state
2. Repository and source revision
3. Run and policy identity
4. High-risk unresolved findings
5. Candidates requiring action
6. Remaining findings and candidate history
7. Operational details and raw evidence links

This prevents a polished summary from hiding invalid or stale evidence.

### Sample project

Present information in this order:

1. Trust label: Sample data
2. What the sample demonstrates
3. Representative finding
4. Candidate and proof lifecycle
5. Clear action to try a real APK

Sample content must not reuse production-looking repository identities without an explicit sample label.

## Target v2 product IA

A persistent v2 product should add hierarchy only when the underlying object exists and can be trusted. Recommended top-level IA:

```text
Projects
Runs
Findings
Candidates
Decisions
Policies
Integrations
Operations
```

The local APK quick check remains a separate utility entry point rather than being mixed into project audit history.

### Projects

Purpose: orient users before showing findings.

- Project list
  - health: healthy, degraded, blocked or unknown
  - latest verified source revision
  - latest valid run time
  - unresolved finding count
  - proof-passed and awaiting-decision counts
- Project detail
  - Overview
  - Runs
  - Findings
  - Candidates
  - Policies
  - Integrations
  - Activity

A project registry must not infer a healthy state from the absence of a run.

### Runs

Purpose: preserve evidence provenance and enable comparison.

- Run list with source commit, policy version, toolset version and evidence state
- Run detail
  - readiness and gate result
  - scanner execution status
  - manifest and source binding
  - findings summary
  - operational diagnostics
  - publication state
- Compare runs
  - added findings
  - resolved findings
  - unchanged findings
  - severity changes caused by policy version changes

Run comparison uses stable finding identities and explains when comparison is invalid because source or policy context is incompatible.

### Findings

Purpose: review immutable deterministic observations.

- Finding list
  - severity
  - scanner and rule
  - file/location
  - first and latest observed run
  - candidate coverage
  - triage metadata, when introduced
- Finding detail
  - evidence and source context
  - occurrence history
  - normalized message and raw-evidence locator
  - related candidates
  - advisory explanation clearly labelled as non-evidence

Future triage state is stored separately from finding evidence so user annotations do not rewrite scanner truth.

### Candidates

Purpose: manage remediation without obscuring lifecycle state.

- Candidate list grouped or filtered by lifecycle
- Candidate detail
  - immutable identity
  - source and finding
  - patch and test metadata
  - branch and fix commit
  - proof status and evidence
  - decision status and actor
  - application status
  - event timeline
  - stale or rejection reason

Primary actions are state-specific. Approval is unavailable before proof passes.

### Decisions

Purpose: give reviewers a focused queue without creating a second lifecycle record.

- Awaiting review
- Approved
- Rejected
- Invalidated by drift
- Decision detail with candidate, proof, reviewer identity and decision time

The queue is a derived candidate view.

### Policies

- Tool pins and versions
- Severity mapping version
- Gate rules
- Baseline, waiver and exception policy when implemented
- Policy change history
- Dry-run impact preview

Policy changes do not retroactively rewrite historical run outcomes.

### Integrations

- Fonebrew proposal connection and last handoff status
- Orrery projection status
- Optional ASOM provider status
- Repository host and notification adapters
- Authentication health without exposed credentials

Each integration page states allowed and forbidden capabilities.

### Operations

- Runner readiness
- Scanner and container pin status
- Workspace capacity and permissions
- MobSF availability and cleanup status
- Audit publication status
- Environment certification evidence

Operations is primarily for the runner operator. Maintainers see summarized failure reasons and escalation paths.

## Mobile v2 IA

Recommended navigation:

1. **Home**
2. **Projects**
3. **Review**
4. **Activity**
5. **Settings**

### Home

- Scan an APK
- Recently opened verified projects or snapshots
- Evidence freshness warnings
- Sample/demo access from help, not as production data

### Projects

- Project status cards
- Latest valid run
- Unresolved findings
- Blocked or unknown reason
- Drill-down to findings and candidates

### Review

- Proof-passed candidates awaiting human decision
- Candidate detail and proof summary
- Authenticated handoff to the trusted decision surface

Direct local mutation is not added merely for convenience. Mobile approval requires a separately authenticated, replay-resistant decision service and an updated threat model.

### Activity

- Recent runs
- New or resolved findings
- Candidate transitions
- Human decisions
- Source-drift invalidations

### Settings

- Imported or connected sources
- Appearance
- Snapshot and local-report retention policy
- Authentication and device trust, if connected mode is introduced
- About, schema version and privacy behavior

## Filters and sorting

Recommended v2 defaults:

- Findings: severity descending, then stable file/rule order
- Candidates: action required first, then lifecycle and recency
- Runs: newest first, with invalid and blocked states distinct
- Projects: blocked, degraded, unknown, then healthy

Useful filters:

- severity
- scanner
- rule
- file path/module
- new/resolved/unchanged since previous run
- candidate lifecycle
- decision required
- source branch or release line, if explicitly modeled

For local APK reports, filters should use local severity and rule category and must remain visibly separate from runner finding filters.

## Empty and failure states

Every major section needs differentiated states:

- No APK selected
- APK inspection in progress
- APK unreadable, malformed or oversized
- Local quick check complete with no observations
- Local quick check complete with observations
- Snapshot not selected
- Not configured
- Configured but never run
- Run in progress
- Ready with zero findings
- Ready with findings
- Unavailable tool or service
- Invalid evidence
- Stale source binding
- Permission denied
- No matching filter results

Do not use a generic empty illustration for states that require user or operator action.

## Accessibility and comprehension requirements

- Status and trust tier must not rely on color alone.
- Fingerprints, hashes and commits must be selectable and use readable monospace treatment.
- Severity and lifecycle labels must use text, not icons alone.
- Dialog-only details in v1 should become full detail screens in v2 for deep links, screen readers and larger evidence sets.
- Long messages need structured truncation with an explicit reveal action.
- Dates include timezone or a relative-plus-absolute representation.
- Copyable commands clearly mark placeholders and the trusted environment in which they execute.
- Local quick-check copy states what was and was not inspected.

## IA governance

Any new feature must answer:

1. Which core object owns this information?
2. Is it local observation, runner evidence, user annotation, policy, operational state or projection?
3. What assurance tier applies?
4. Is it mutable, and by which actor?
5. Does it require source-revision binding?
6. Does it change the authority matrix?
7. Can it be represented without weakening fail-closed states?

If those questions do not have clear answers, the feature is not ready to enter the IA.