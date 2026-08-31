# Personas and authority roles

This document separates human product personas from machine actors. A persona describes goals and context; an actor identifies what may perform a lifecycle transition.

These personas are provisional archetypes inferred from the implemented product and workflows. They are not a substitute for interviews or contextual research.

## Primary persona: Android maintainer

### Context

Maintains an Android application or library and is accountable for shipping changes without introducing known security regressions. This may be a solo open-source maintainer, a technical lead or a developer in a small product team.

### Goals

- Get immediate value from an APK without configuring infrastructure first.
- Know whether the current source revision has valid runner evidence.
- Understand which findings need attention first.
- Receive or create a bounded remediation proposal.
- Verify that a fix corresponds to the exact finding and source revision.
- Move a valid fix into the repository's normal review process.

### Current tasks

- Quick-check an APK on the Android device before release or distribution.
- Distinguish local package observations from runner-verified findings.
- Trigger or inspect an audit run.
- Review normalized findings and source locations.
- Create or request a candidate patch and proving test.
- Prepare the dedicated candidate branch.
- Inspect proof and reviewer decisions.
- Continue with the repository's existing PR and merge process after Assay records application.

### Pain points Assay addresses

- Security tools often require setup before the user sees any product value.
- Scanner output from different tools is inconsistent and difficult to compare.
- A green-looking report may actually represent a failed or missing scanner.
- APK package risks may be missed during release handoff.
- AI-generated fixes can lose connection to original evidence.
- A patch may be reviewed without proof against the exact source revision.
- Audit history can be rewritten or mixed with source history.

### Trust concerns

- Must know whether a result is local quick check, sample data or runner-verified evidence.
- Must be able to distinguish deterministic evidence from model explanation.
- Must know when evidence is stale.
- Must not be pushed into automatic merge or approval.
- Must see exact source, finding, patch and proof binding.

### Success criteria

- Can inspect a selected APK without uploading it.
- Correctly understands that local observations are not source-level certification.
- Can determine runner-audit readiness and top unresolved risks quickly.
- Can trace a candidate to the original finding without manual reconstruction.
- Can hand an applied candidate into normal code review with complete evidence.

## Primary persona: Security reviewer and approver

### Context

A human responsible for accepting or rejecting a proof-passed remediation. This may be a security engineer, senior maintainer, release owner or explicitly authorized reviewer.

### Goals

- Review deterministic evidence rather than trusting a recommendation summary.
- Confirm the candidate is bound to the intended source revision and finding.
- Understand proof result and residual risk.
- Make an attributable decision that cannot be forged by an integration.

### Current tasks

- Inspect finding, candidate identity, patch digest and test digest.
- Review proof evidence and scanner replay.
- Confirm candidate branch and fix commit.
- Approve or reject using an explicit `human:<identity>` actor.
- Re-review when source drift or revision changes invalidate earlier context.

### Pain points Assay addresses

- Approval requests often omit source and proof provenance.
- Security decisions are buried in chat or PR comments without structured binding.
- Reviewers cannot easily tell whether a tool silently failed.
- AI summaries can overstate confidence.

### Trust concerns

- A local APK quick check must not be offered as sufficient proof for a candidate.
- Approval must be a human act, not a delegated model or service action.
- Reviewed proof must be the proof stored in the candidate.
- A decision must not remain valid after source or candidate drift.
- `approved` must not be presented as merged, released or risk-free.

### Success criteria

- Can reconstruct why a decision was made from the stored record.
- Can reject incomplete or stale evidence without ambiguity.
- Can identify the responsible human identity for every decision.

## Supporting persona: Release tester or APK evaluator

### Context

Receives an APK for testing, internal distribution or release validation but may not have repository access or a configured Assay runner. This role may be performed by the maintainer, QA, product owner or technical evaluator.

### Goals

- Confirm basic package identity and release metadata.
- Spot obvious package-level security concerns quickly.
- Avoid uploading an internal APK to an unknown service.
- Know when deeper source-level audit is required.

### Current tasks

- Select an APK from device storage.
- Review package, signing, permission, exported-component and native-library information.
- Share the SHA-256 with the maintainer through an existing trusted channel.
- Escalate to the runner-verified audit when package-level inspection is insufficient.

### Trust concerns

- The app must state exactly what was and was not checked.
- A clean local quick check must not be interpreted as a clean source audit.
- APK data must remain local to the device.

### Success criteria

- Can complete the quick check without technical runner knowledge.
- Can correctly explain the result's assurance level to another person.
- Can identify the next step when a concerning observation appears.

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
- Run Assay preflight.
- Configure systemd hardening and private runtime directories.
- Provide credentials without placing them in audit artifacts.
- Diagnose unavailable, invalid or cleanup-failed states.
- Certify the real environment using the verification checklist.

### Pain points Assay addresses

- Self-hosted runners accumulate mutable state.
- Tool versions drift from configuration.
- Successful process exit does not guarantee complete evidence.
- Container and API cleanup failures can be hidden.

### Trust concerns

- Executables must match expected digests and versions.
- Workspaces must be private and disposable.
- Cancelled jobs must not leave trusted-looking partial output.
- Production certification must use live evidence, not repository tests alone.

### Success criteria

- Preflight failures are specific and actionable.
- Repeated runs on the same source and inputs produce equivalent canonical evidence.
- Operator can prove the environment used for a run.

## Supporting persona: Portfolio observer

### Context

Needs a compact view of whether applications are healthy, degraded, blocked or unknown. In the constellation this is primarily served by Orrery.

### Goals

- See whether valid evidence exists.
- Identify projects with unresolved findings or blocked evidence.
- Know when status is stale or unavailable.
- Drill into Assay without receiving mutation authority.

### Current tasks

- Read Orrery status projection.
- Compare current state with operational expectations.
- Route attention to maintainers or operators.

### Trust concerns

- Health must derive from verified evidence, not local quick checks.
- Projection remains read-only.
- `unknown` and `blocked` are not shown as healthy.

### Success criteria

- Can prioritize attention without opening raw scanner artifacts.
- Can distinguish operational failure from unresolved security findings.

## Supporting persona: Assisted remediator

### Context

Uses Fonebrew, and optionally ASOM, to understand a verified finding or produce a proposed patch and proving test. The human may overlap with the maintainer persona.

### Goals

- Reduce effort required to interpret and remediate a finding.
- Generate a proposal that Assay can bind to verified evidence.
- Retain a manual workflow when Studio automation is unavailable.

### Current tasks

- Request explanation for a verified finding.
- Produce patch and test artifacts.
- Submit digests through the Fonebrew proposal gateway.
- Hand the candidate to proof and human review.

### Trust concerns

- Model output is not evidence.
- Integration cannot invent a finding fingerprint.
- Local APK observations cannot be used as candidate origins.
- Proposal authority does not expand into proof, approval or application.

### Success criteria

- Proposal preserves exact finding and source context.
- Manual and Studio-assisted paths produce equivalent candidate contracts.

## Human roles and permissions

| Capability | Maintainer | Reviewer | APK evaluator | Runner operator | Portfolio observer |
|---|---:|---:|---:|---:|---:|
| Run local APK quick check | Yes | Optional | Yes | Optional | No |
| Read local APK report | Yes | Optional | Yes | Optional | No |
| Read runner-verified findings | Yes | Yes | Only when shared | Yes | Projection only |
| Inspect raw operational diagnostics | Optional | Optional | No | Yes | No |
| Create manual candidate inputs | Yes | Optional | No | No | No |
| Request Fonebrew proposal | Yes | Optional | No | No | No |
| Execute proof workflow | No by role | No | No | Operates environment | No |
| Approve or reject candidate | Only when acting as reviewer | Yes | No | No by default | No |
| Record application | No; executor does this | No | No | Operates executor | No |
| Merge repository change | Repository governance | Repository governance | No | No by Assay role | No |
| Configure tools and policies | Optional in small teams | Advisory | No | Yes | No |
| Read Orrery health | Optional | Optional | No | Yes | Yes |

The table describes expected product roles, not hard-coded identity management. v1 enforces lifecycle actor classes, while organization-specific authorization remains part of deployment and repository governance.

## Machine and product actors

| Actor | Allowed responsibility | Explicitly forbidden |
|---|---|---|
| Android local auditor | Read a user-selected APK through the document picker; create an in-memory package-level report; delete the temporary copy | Network upload, runner finding creation, candidate creation, proof, decision, application, merge |
| Android verified-audit viewer | Validate and display a strict projection; copy command templates | Runner execution, proof, approval, rejection, application, merge |
| Android sample provider | Display built-in labelled demonstration data | Representing sample content as evidence |
| `system:assay` | Create and propose Assay-originated candidates | Human decisions, proof fabrication, merge |
| `system:fonebrew` | Propose against an existing verified finding | Finding creation, proof, approval, rejection, application |
| `system:proof-runner` | Record mechanically validated proof | Human decision or merge |
| `human:<identity>` | Approve or reject a proof-passed candidate | Bypassing proof or revision checks |
| `system:executor` | Record valid application for approved fix commit | Merge or deploy by virtue of the record |
| `system:source-watch` | Mark stale when source binding no longer holds | Re-approve or rewrite identity |
| Orrery reader | Read health projection | Candidate mutation |
| ASOM provider | Return advisory explanation | Evidence or lifecycle mutation |

## Persona validation plan

For v2 discovery, interview at least:

- three Android maintainers with different repository sizes;
- two people who approve security-sensitive changes;
- two operators of self-hosted CI runners;
- two people who receive APKs without repository access;
- one portfolio or engineering leader consuming security status.

Validate:

- frequency and value of local APK inspection;
- whether trust labels are correctly understood;
- false-positive tolerance for package-level observations;
- handoff friction between APK evaluation and source audit;
- decision evidence requirements;
- mobile context and accessibility needs;
- acceptable notification channels;
- waiver, export and sharing requirements.

Record contradictions rather than averaging them away.