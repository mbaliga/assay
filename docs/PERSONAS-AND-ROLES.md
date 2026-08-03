# Personas and authority roles

This document separates human product personas from machine actors. The distinction is important: a persona describes goals and context, while an actor identifies what may perform a lifecycle transition.

These personas are provisional archetypes inferred from the current v1 product. They are not a substitute for interviews or contextual research.

## Primary persona: Android maintainer

### Context

Maintains an Android application or library and is accountable for shipping changes without introducing known security regressions. This may be a solo open-source maintainer, a technical lead or a developer in a small product team.

### Goals

- Know whether the current source revision has valid security evidence.
- Understand which findings need attention first.
- Receive or create a bounded remediation proposal.
- Verify that a fix corresponds to the exact finding and source revision.
- Move a valid fix into the repository's normal review process.

### Current tasks

- Trigger or inspect an audit run.
- Review normalized findings and source locations.
- Create or request a candidate patch and proving test.
- Prepare the dedicated candidate branch.
- Inspect proof and reviewer decisions.
- Continue with the repository's existing PR and merge process after Assay records application.

### Pain points Assay addresses

- Scanner output from different tools is inconsistent and difficult to compare.
- A green-looking report may actually represent a failed or missing scanner.
- AI-generated fixes can lose the connection to the original evidence.
- A patch may be reviewed without proof against the exact source revision.
- Audit history can be rewritten or mixed with source history.

### Trust concerns

- Must be able to distinguish deterministic evidence from model explanation.
- Must know when evidence is stale.
- Must not be pushed into an automatic merge or approval path.
- Must see the exact source, finding, patch and proof binding.

### Success criteria

- Can determine audit readiness and top unresolved risks quickly.
- Can trace a candidate to the original finding without manual reconstruction.
- Can hand an applied candidate into the normal code-review process with complete evidence.

## Primary persona: Security reviewer and approver

### Context

A human responsible for accepting or rejecting a proof-passed remediation. This may be a security engineer, senior maintainer, release owner or another explicitly authorized reviewer.

### Goals

- Review deterministic evidence rather than trusting a recommendation summary.
- Confirm the candidate is bound to the intended source revision and finding.
- Understand the proof result and residual risk.
- Make an attributable decision that cannot be forged by an integration.

### Current tasks

- Inspect the finding, candidate identity, patch digest and test digest.
- Review proof evidence and scanner replay results.
- Confirm the candidate branch and fix commit.
- Approve or reject using an explicit `human:<identity>` actor.
- Re-review when source drift or candidate revision changes invalidate the earlier context.

### Pain points Assay addresses

- Approval requests often omit source and proof provenance.
- Security decisions are buried in chat or PR comments without structured binding.
- Reviewers cannot easily tell whether a tool silently failed.
- AI-generated summaries can overstate confidence.

### Trust concerns

- Approval must be a human act, not a delegated model or service action.
- The reviewed proof must be the same proof recorded in the candidate.
- A decision must not remain valid after source or candidate drift.
- `approved` must not be presented as equivalent to merged, released or risk-free.

### Success criteria

- Can reconstruct why a decision was made from the stored record.
- Can reject incomplete or stale evidence without ambiguity.
- Can identify the responsible human identity for every decision.

## Supporting persona: Runner operator and platform steward

### Context

Installs and operates the trusted execution environment, including the Dell/self-hosted runner, pinned scanner binaries, MobSF container and private work directories.

### Goals

- Keep scanner and proof execution reproducible.
- Detect configuration drift before an audit is trusted.
- Prevent secrets, unsafe permissions or stale artifacts from contaminating evidence.
- Recover safely from failed or cancelled runs.

### Current tasks

- Install exact tool and container pins.
- Run the Assay preflight.
- Configure systemd hardening and private runtime directories.
- Provide credentials without placing them in audit artifacts.
- Diagnose unavailable, invalid or cleanup-failed states.
- Certify the real environment using the verification checklist.

### Pain points Assay addresses

- Self-hosted runners frequently accumulate mutable state.
- Tool versions may drift from configuration.
- Successful process exit does not guarantee complete evidence.
- Container and API cleanup failures can be hidden.

### Trust concerns

- Scanner executables must match expected digests and versions.
- Workspaces must be private and disposable.
- A cancelled job must not leave trusted-looking partial output.
- Production certification must be based on live evidence, not repository tests alone.

### Success criteria

- Preflight failures are specific and actionable.
- Repeated runs on the same source and inputs produce equivalent canonical evidence.
- The operator can prove the environment used for a run.

## Supporting persona: Portfolio observer

### Context

Needs a compact view of whether one or more applications are healthy, degraded, blocked or unknown. In the constellation this is primarily served by Orrery.

### Goals

- See whether valid evidence exists.
- Identify projects with unresolved findings or blocked evidence.
- Know when a status is stale or unavailable.
- Drill into Assay for details without receiving mutation authority.

### Current tasks

- Read the Orrery status projection.
- Compare current state with operational expectations.
- Route attention to maintainers or operators.

### Pain points Assay addresses

- Portfolio dashboards often collapse no data and no findings.
- High-level status can hide stale or invalid evidence.
- Observability tools sometimes gain unintended operational control.

### Trust concerns

- Health must be derived from verified evidence.
- The projection must remain read-only.
- `unknown` and `blocked` must not be shown as healthy.

### Success criteria

- Can prioritize attention without opening raw scanner artifacts.
- Can distinguish operational failure from unresolved security findings.

## Supporting persona: Assisted remediator

### Context

Uses Fonebrew, and optionally ASOM, to understand a finding or produce a proposed patch and proving test. The human user may overlap with the Android maintainer persona.

### Goals

- Reduce the effort required to interpret and remediate a finding.
- Generate a proposal that Assay can bind to verified evidence.
- Retain a manual workflow when Studio automation is unavailable.

### Current tasks

- Request an explanation for a verified finding.
- Produce patch and test artifacts.
- Submit digests through the Fonebrew proposal gateway.
- Hand the resulting candidate to proof and human review.

### Trust concerns

- Model output must not be confused with evidence.
- The integration must not invent a finding fingerprint.
- Proposal authority must not expand into proof, approval or application authority.

### Success criteria

- A useful proposal arrives with the exact finding and source context preserved.
- Manual and Studio-assisted paths produce equivalent Assay candidate contracts.

## Human roles and permissions

| Capability | Maintainer | Security reviewer | Runner operator | Portfolio observer |
|---|---:|---:|---:|---:|
| Read verified findings | Yes | Yes | Yes | Projection only |
| Inspect raw operational diagnostics | Optional | Optional | Yes | No |
| Create manual candidate inputs | Yes | Optional | No | No |
| Request Fonebrew proposal | Yes | Optional | No | No |
| Execute proof workflow | No by role; delegated to proof runner | No | Operates environment | No |
| Approve or reject candidate | Only when explicitly acting as reviewer | Yes | No by default | No |
| Record application | No; executor does this | No | Operates executor | No |
| Merge repository change | Through repository governance | Through repository governance | No by Assay role | No |
| Configure tools and policies | Optional in small teams | Advisory | Yes | No |
| Read Orrery health | Optional | Optional | Yes | Yes |

The table describes expected product roles, not hard-coded identity management. v1 enforces lifecycle actor classes, while organization-specific authorization remains part of deployment and repository governance.

## Machine actors

| Actor | Allowed responsibility | Explicitly forbidden |
|---|---|---|
| `system:assay` | Create and propose Assay-originated candidates | Human decisions, proof fabrication, merge |
| `system:fonebrew` | Propose against an existing verified finding | Finding creation, proof, approval, rejection, application |
| `system:proof-runner` | Record mechanically validated proof | Human decision or merge |
| `human:<identity>` | Approve or reject a proof-passed candidate | Pretending to be a system actor; bypassing proof |
| `system:executor` | Record valid application for the approved fix commit | Merge or deploy by virtue of the Assay record |
| `system:source-watch` | Mark a candidate stale when source binding no longer holds | Re-approve or rewrite identity |
| Orrery reader | Read health projection | Candidate mutation |
| ASOM provider | Return advisory explanation | Evidence or lifecycle mutation |
| Android console | Validate and display a projection; copy command templates | Network access, proof, approval, rejection, application or merge |

## Persona validation plan

For v2 discovery, interview at least:

- three Android maintainers with different repository sizes;
- two people who approve security-sensitive changes;
- two operators of self-hosted CI runners;
- one portfolio or engineering leader who consumes security status.

Validate task frequency, handoff friction, decision evidence, mobile-console context, acceptable notification channels and waiver requirements. Record contradictions rather than averaging them away.