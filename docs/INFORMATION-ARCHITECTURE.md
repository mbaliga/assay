# Information architecture

This document defines how Assay's information should be grouped, related and presented across the CLI, Android console and future product surfaces.

The IA follows the trust model. It must not collapse evidence, remediation, human decisions and operational state into a single generic issue object.

## IA principles

1. **Source context is always visible.** Every run, finding and candidate is understood against an exact repository and source commit.
2. **Evidence state precedes findings.** Users must first know whether evidence is ready, unavailable, invalid, stale or not configured.
3. **Findings and candidates remain separate.** A finding is immutable scanner evidence; a candidate is a proposed remediation.
4. **Proof and approval are separate stages.** A passed proof is not a human decision, and approval is not application or merge.
5. **Operational diagnostics are available without polluting the review hierarchy.** Maintainers see a concise state; operators can inspect deeper causes.
6. **Read-only projections remain visibly read-only.** Android, Orrery and ASOM surfaces must not imply authority they do not possess.
7. **State labels use the contract language.** UI copy should not invent softer synonyms for blocked or invalid states.

## System-level object model

```text
Project / repository
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

## Current v1 IA

### CLI command domains

The CLI should be understood as six command families, even though commands remain flat for shell usability:

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

The documentation and future help output should group commands this way rather than presenting an undifferentiated alphabetical list.

### Android console IA

The v1 Android console is intentionally shallow and read-only:

```text
Launch / no snapshot
  └── Open verified snapshot
        ├── Evidence status
        │     ├── Ready
        │     ├── Not configured
        │     ├── Unavailable
        │     └── Invalid / stale reason
        │
        └── Ready snapshot
              ├── Overview
              │     ├── Repository
              │     ├── Source commit
              │     ├── Run ID
              │     ├── Generated time
              │     ├── Finding count
              │     ├── Unresolved count
              │     └── Candidate count
              ├── Findings
              │     └── Finding detail dialog
              │           ├── Rule and message
              │           ├── Scanner and severity
              │           ├── Location
              │           ├── Fingerprint
              │           └── Related candidate count
              └── Candidates
                    └── Candidate detail dialog
                          ├── Lifecycle
                          ├── Revision
                          ├── Fix branch
                          ├── Human approval visibility
                          ├── Finding fingerprint
                          └── Approval/rejection command handoff when eligible
```

This is an acceptable v1 review IA because there is one imported snapshot and no persistent project registry.

## Target v2 product IA

A persistent v2 product should add hierarchy only when the underlying object exists and can be trusted. The recommended top-level IA is:

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

Run comparison should be based on stable finding identities and should show when comparison is invalid because source or policy context is incompatible.

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

Future triage state must be stored separately from the finding evidence so user annotations do not rewrite scanner truth.

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

Primary actions must be state-specific. A user should never see an enabled approval action before proof passes.

### Decisions

Purpose: give reviewers a focused queue without turning approval into a generic notification.

- Awaiting review
- Approved
- Rejected
- Invalidated by drift
- Decision detail with candidate, proof, reviewer identity and decision time

The decision queue is a derived view of candidates. It must not create a second mutable lifecycle record.

### Policies

Purpose: make tool, severity and gate behavior inspectable.

- Tool pins and versions
- Severity mapping version
- Gate rules
- Baseline, waiver and exception policy when implemented
- Policy change history
- Dry-run impact preview for future policy changes

Policy editing should be restricted to authorized operators and should never retroactively rewrite old run outcomes. Old runs retain their original policy version.

### Integrations

- Fonebrew proposal connection and last handoff status
- Orrery projection status
- Optional ASOM provider status
- Repository host and notification adapters in v2
- Authentication health without exposing credentials

Each integration detail page must state its allowed capabilities and forbidden capabilities.

### Operations

- Runner readiness
- Scanner and container pin status
- Workspace capacity and permission checks
- MobSF availability and cleanup status
- Audit publication status
- Environment certification evidence

Operations is primarily for the runner operator. Maintainers may see a summarized failure reason and escalation path.

## Mobile v2 IA

The Android console should remain a review and status surface unless the security model is intentionally redesigned.

Recommended mobile navigation:

1. **Projects**
2. **Review**
3. **Activity**
4. **Settings**

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

Direct local mutation should not be added merely for convenience. If mobile approval is introduced, it requires a separately authenticated, replay-resistant decision service and an updated threat model.

### Activity

- Recent runs
- New or resolved findings
- Candidate transitions
- Human decisions
- Source-drift invalidations

### Settings

- Imported or connected sources
- Appearance
- Snapshot retention policy
- Authentication and device trust, if a connected mode is introduced
- About, schema version and privacy behavior

## Content hierarchy within a screen

For a project or snapshot, present information in this order:

1. Evidence availability and blocking state
2. Repository and source revision
3. Run and policy identity
4. High-risk unresolved findings
5. Candidates requiring action
6. Remaining findings and candidate history
7. Operational details and raw evidence links

This prevents a polished summary from hiding invalid or stale evidence.

## Filters and sorting

Recommended v2 defaults:

- Findings: severity descending, then stable file/rule order
- Candidates: action required first, then lifecycle and recency
- Runs: newest first, with invalid and blocked states visually distinct
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

## Empty and failure states

Every major section needs differentiated states:

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

Do not use a generic empty illustration for states that require operator action.

## Accessibility and comprehension requirements

- Status must not rely on color alone.
- Fingerprints and commits must be selectable and rendered in a readable monospace treatment.
- Severity and lifecycle labels must use text, not icons alone.
- Dialog-only details in v1 should become full detail screens in v2 to support deep links, screen readers and larger evidence sets.
- Long scanner messages need structured truncation with an explicit reveal action.
- Dates should include timezone or a relative-plus-absolute representation.
- Copyable commands must clearly mark placeholders and the trusted environment in which they must execute.

## IA governance

Any new feature must answer:

1. Which core object owns this information?
2. Is it evidence, user annotation, policy, operational state or projection?
3. Is it mutable, and by which actor?
4. Does it require source-revision binding?
5. Does it change the current authority matrix?
6. Can it be represented without weakening fail-closed states?

If those questions do not have clear answers, the feature is not ready to enter the IA.