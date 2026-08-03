# Product experience model

This document defines the product-level intent behind Assay. It complements the normative trust contract, threat model and implementation documentation; it does not override them.

The personas and needs in this document are provisional product hypotheses derived from the implemented v1 workflows. They should be validated with real maintainers, reviewers and operators before they are treated as user-research findings.

## Product promise

Assay helps an Android project answer five questions without asking the user to trust an AI model or an opaque dashboard:

1. What did deterministic tools find in this exact source revision?
2. Can the evidence be verified and distinguished from missing, stale or invalid evidence?
3. What fix is being proposed for a specific finding?
4. Was that exact fix mechanically proved and explicitly accepted by a human?
5. What happened afterwards, and can the record be audited independently of source history?

The product is successful when a maintainer can move from a verified finding to a reviewable, proof-bound fix without losing source, evidence, actor or approval provenance.

## Product principles

### Deterministic evidence before explanation

Scanner output, source binding, proof and lifecycle state are the product's source of truth. Human-readable explanations and AI assistance are secondary projections.

### Missing is not clean

`not configured`, `unavailable`, `invalid`, `stale` and `ready with zero findings` are distinct product states. The interface must never collapse them into one neutral or successful state.

### Human authority remains explicit

AI, Fonebrew and other integrations may explain or propose. They cannot originate findings, record proof, approve, reject, apply or merge fixes. Approval and rejection remain attributable human acts.

### Every action has an inspectable object

Findings, runs, candidates, proofs, approvals and applications are separate objects. The product should show their relationships instead of presenting remediation as an untraceable one-click action.

### Safe defaults over convenience

Source drift, invalid evidence, stale revisions, unknown scanners and failed cleanup block progress. v2 convenience features must preserve this fail-closed behavior.

### Independent utility

Assay remains useful without Fonebrew, Orrery or ASOM. Constellation integrations improve handoff and visibility but do not become hidden runtime dependencies.

## Core product objects

- **Project**: an Android source repository under audit. v1 operates on one repository invocation at a time; a persistent project registry is a v2 opportunity.
- **Source revision**: the exact commit against which evidence and candidates are bound.
- **Audit run**: one deterministic execution of the configured scanners and policy.
- **Finding**: a normalized scanner observation with a stable fingerprint. It is immutable evidence, not a task record.
- **Evidence bus**: the validated local publication and disconnected `assay/audit` history for audit artifacts.
- **Candidate**: a proposed remediation bound to a finding, patch and proving test.
- **Proof**: fail-before/pass-after and scanner-replay evidence for the exact candidate.
- **Decision**: an explicit human approval or rejection of a proof-passed candidate.
- **Application record**: deterministic confirmation that the approved fix commit satisfies the candidate contract. It is not a merge.
- **Projection**: a read-only representation for the Android console, Orrery or an advisory integration.
- **Policy**: severity mapping, gates, tool pins and future configurable rules that determine readiness and blocking behavior.

## v1 product surfaces

### Assay CLI

The CLI is the authoritative control surface for scanner execution, evidence publication, candidate transitions, runner preflight and projections. It is optimized for trusted operators and automation rather than casual browsing.

### Android review console

The Android console is a read-only evidence viewer. It imports a bounded, validated snapshot and presents availability, run identity, findings and candidate state. It copies explicit approval/rejection command templates but does not execute them.

### Fonebrew proposal boundary

Fonebrew can propose a patch/test pair only for an existing verified finding. Studio may make transfer seamless, but the authority boundary does not change.

### Orrery health projection

Orrery receives a compact status projection for portfolio visibility. It cannot mutate findings or candidates.

### ASOM advisory boundary

ASOM may explain deterministic evidence. Model output is not evidence and cannot alter lifecycle state.

## Product scope

### Included in v1

- deterministic security auditing for Android repositories;
- verifiable, source-bound evidence;
- disconnected audit history;
- proof-bound remediation candidates;
- explicit human decisions;
- read-only console and constellation projections;
- hardened self-hosted-runner deployment assets.

### Explicitly outside v1

- multi-project portfolio management inside Assay;
- user accounts, organizations or team administration;
- web-hosted mutable dashboards;
- automatic approval, merge or deployment;
- general-purpose issue tracking;
- scanner marketplace or third-party plugin SDK;
- release distribution and device management;
- claiming deployment certification without live-environment evidence.

## Experience outcomes

A good Assay experience should make the following true:

- A maintainer can tell whether an audit is valid within seconds.
- A reviewer can trace a candidate back to the exact finding, source commit, patch, test and proof.
- An operator can diagnose unavailable or invalid states without inspecting raw implementation internals first.
- A human decision is clearly attributable and cannot be confused with AI advice.
- A portfolio observer can distinguish healthy, degraded, blocked and unknown projects.
- No interface implies that an application record merged or deployed a fix.

## Product language

Use these labels consistently:

- **Audit run**, not scan session when the complete multi-tool run is meant.
- **Finding**, not issue when referring to immutable scanner evidence.
- **Candidate**, not fix until proof and approval context is visible.
- **Proof passed**, not fixed.
- **Approved**, not safe.
- **Applied**, only for the Assay application record; never imply merged or released.
- **Unavailable**, **invalid**, **stale**, **not configured** and **ready** as distinct evidence states.
- **Human decision** when attribution matters.

## Product questions to validate

Before broadening v2, validate these assumptions with users:

- Whether the primary daily user is the Android maintainer, a security reviewer or a platform operator.
- Whether approval should remain CLI-only or move to a separately authenticated review service.
- Whether the mobile console is used during development, incident review, executive oversight or all three.
- What evidence reviewers require before accepting a candidate beyond the current mechanical proof.
- How teams want to handle accepted risk, false positives, waivers and expiry.
- Whether projects need continuous monitoring or explicitly initiated audits.
- Which integrations are essential for a non-Studio Fonebrew workflow.

Until those questions are validated, v2 should improve observability and workflow clarity without increasing authority or automation.