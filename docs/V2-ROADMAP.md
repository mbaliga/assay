# Assay v2 roadmap

v1 establishes the deterministic trust engine, evidence bus, candidate lifecycle, narrow integrations, runner hardening assets and a read-only Android console. v2 should turn those verified primitives into an operational product for repeated use across projects and teams without weakening the authority model.

This roadmap separates deployment certification from new product capability. Uncertified v1 deployment work is not rebranded as v2.

## Before v2: deployment certification and v1.1 hardening

These items are required to call the deployed system operationally verified:

1. Install the Dell/self-hosted runner and capture successful preflight evidence.
2. Exercise cancellation, cleanup, workspace isolation and restart recovery.
3. Run the pinned MobSF container against a representative real APK.
4. Publish and verify the real hosted `assay/audit` branch using production credentials.
5. Import a real snapshot on an Android device and complete accessibility/usability smoke tests.
6. Establish debug and release signing, artifact retention and distribution procedure.
7. Connect and exercise the installed Fonebrew and Orrery transports.
8. Exercise optional ASOM explanation only when an ASOM provider is configured.
9. Perform a threat-model review against the actual network, identity and credential topology.
10. Record evidence in `docs/VERIFICATION.md` or a deployment-specific certification record.

Recommended v1.1 hardening after first live use:

- structured operational error codes;
- cancellation-safe temporary-directory cleanup;
- disk-pressure and evidence-retention policy;
- backup and restore procedure for candidate records and audit history;
- log redaction review using real failures;
- release signing and reproducible CLI/APK build notes;
- migration test for every schema version change.

## v2 product thesis

Assay v2 should answer:

> Across the Android projects I am responsible for, what evidence is valid, what changed, which findings require action, which remediations are awaiting proof or decision, and what can I safely do next?

The v2 product is not an autonomous repair agent. It is a multi-project evidence, review and controlled-remediation system.

## v2 principles

- Preserve deterministic scanners as the only finding writers.
- Preserve immutable evidence and candidate identities.
- Keep human decisions explicit and attributable.
- Add convenience through projections, queues and integrations rather than hidden authority.
- Prefer a read model over shared mutable integration state.
- Version policy and retain the policy used for every historical run.
- Treat accepted risk and false-positive triage as annotations, not edits to findings.
- Do not introduce automatic merge as a default or implied end state.

## Priority 0: operational product foundation

### Epic A: Project and run registry

Build a persistent registry above the current single-run artifacts.

Capabilities:

- register repositories and ownership;
- record latest verified source revision and run;
- preserve run history and policy/toolset versions;
- distinguish never run, unavailable, invalid, stale, ready with findings and ready clean;
- generate read-only project summaries for Android and Orrery;
- avoid storing repository credentials in project records.

Acceptance criteria:

- No project can appear healthy without a valid run within policy freshness.
- Every run links to immutable evidence and exact source commit.
- Deleting a project registration does not rewrite disconnected audit history.

### Epic B: Connected read model

Replace manual snapshot transfer as the only convenient path while preserving offline import.

Capabilities:

- authenticated read-only API or signed snapshot feed;
- incremental retrieval of projects, runs, findings and candidates;
- Android local cache with explicit freshness and source identity;
- offline mode and manual import remain supported;
- no mobile mutation authority in this epic.

Acceptance criteria:

- Connected and imported views validate the same schema and object invariants.
- Stale cache cannot be displayed as current without a visible freshness warning.
- The Android app still requests no authority to run scanners or mutate candidates.

### Epic C: Production identity and authorization

Map organizational identities to product roles without replacing lifecycle actor checks.

Capabilities:

- authenticated users and service identities;
- project-scoped maintainer, reviewer, operator and observer roles;
- reviewer eligibility policy;
- service credentials for runner, Fonebrew and Orrery transports;
- complete authorization audit log.

Acceptance criteria:

- Authentication alone does not grant approval rights.
- Machine identities cannot impersonate `human:<identity>`.
- Role changes do not rewrite historical decisions.

## Priority 1: review and triage experience

### Epic D: Findings workspace

Capabilities:

- search, filter, sort and saved views;
- stable finding detail pages;
- occurrence history across compatible runs;
- new, resolved and unchanged comparison;
- file/module ownership;
- candidate coverage and current remediation state;
- advisory ASOM explanation visibly separated from evidence.

Acceptance criteria:

- Run comparison explains incompatibility when policy or identity rules changed.
- Filtering never changes counts without indicating active filters.
- Raw evidence remains locatable from normalized findings.

### Epic E: Triage annotations and accepted risk

Add human workflow metadata without mutating finding evidence.

Capabilities:

- owner, status and reviewer notes;
- false-positive assertion;
- accepted-risk waiver with reason, approver, scope and expiry;
- duplicate and related-finding links;
- automatic waiver expiry and re-review queue;
- policy-defined restrictions by severity or rule.

Acceptance criteria:

- A waiver never removes the finding from historical evidence.
- Expired waivers cannot suppress current health silently.
- Every exception is attributable and time-bound by default.

### Epic F: Decision queue

Capabilities:

- proof-passed candidates awaiting review;
- complete candidate/proof context in one review surface;
- approve/reject with rationale;
- reviewer eligibility and optional two-person policy;
- invalidation when candidate revision or source binding changes;
- notification and escalation without auto-decision.

Acceptance criteria:

- A decision request cannot execute against a stale revision.
- Approval UI distinguishes proof passed, approved, applied, merged and released.
- Mobile approval remains out of scope until a dedicated threat-model review approves it.

## Priority 2: policy and evidence intelligence

### Epic G: Versioned policy packs

Capabilities:

- project and organization policy layers;
- scanner enablement and required-tool rules;
- severity mapping and gate thresholds;
- evidence freshness policy;
- waiver restrictions;
- policy dry-run against historical evidence;
- signed/versioned policy release.

Acceptance criteria:

- Historical run conclusions retain the policy version used at execution.
- A policy change creates a new evaluation result rather than rewriting evidence.
- Missing required scanners block readiness.

### Epic H: Baselines and differential audits

Capabilities:

- compatible baseline selection;
- added, resolved and persistent findings;
- release-branch or protected-branch baseline rules;
- differential gates for newly introduced findings;
- full-run verification remains periodically required.

Acceptance criteria:

- Differential mode cannot hide unavailable scanners.
- Baseline identity and compatibility are visible.
- A clean differential result is not presented as a clean full audit.

### Epic I: Evidence attestation

Capabilities:

- sign run manifests and projection documents;
- record runner/environment identity;
- verify signatures before connected clients trust data;
- key rotation and revocation procedure;
- export a portable evidence bundle.

Acceptance criteria:

- Signature verification failure produces invalid evidence.
- Signing keys never appear in audit artifacts or mobile storage.
- Rotation preserves verification of historical records.

## Priority 3: safe remediation at team scale

### Epic J: Disposable proof workers

Capabilities:

- isolated proof jobs per candidate;
- bounded CPU, memory, network and time;
- explicit dependency/cache policy;
- proof queue, cancellation and retry;
- tamper-evident proof artifact retention;
- flakiness diagnostics.

Acceptance criteria:

- Proof workers cannot approve, apply or merge.
- A retry cannot overwrite a previous proof record silently.
- Network access is denied by default and explicitly declared when required.

### Epic K: Repository-host integration

Capabilities:

- create or update draft PRs after candidate proof and approval;
- attach immutable evidence locators and candidate identity;
- publish status checks for evidence, proof, approval and source freshness;
- observe merge result and trigger a new audit;
- support manual operation when the integration is absent.

Acceptance criteria:

- Assay never presses merge by default.
- Repository protections remain authoritative.
- Merge observation creates new evidence; it does not rewrite candidate history.

### Epic L: Competing proposal comparison

Capabilities:

- multiple candidates for one finding;
- compare patch scope, test coverage, proof outcome and residual scanner evidence;
- supersede or withdraw without deleting history;
- preserve independent candidate identities.

Acceptance criteria:

- Selecting one proposal does not mutate or erase alternatives.
- Reviewers can see why a candidate was superseded.

## Priority 4: constellation and portfolio experience

### Epic M: Orrery portfolio posture

Capabilities:

- project health and evidence freshness;
- unresolved-risk ageing;
- blocked-run root causes;
- awaiting-proof and awaiting-review counts;
- ownership and escalation routing;
- read-only drill-down to Assay.

Acceptance criteria:

- Unknown and blocked never aggregate into healthy.
- Portfolio metrics link back to verifiable source objects.
- Orrery receives no candidate mutation capability.

### Epic N: Fonebrew Studio workflow

Capabilities:

- context-rich handoff from finding to proposal;
- secure transfer of patch/test artifacts and digests;
- proposal progress visible in Fonebrew;
- manual equivalent for users without Studio;
- clear display of where Fonebrew authority ends.

Acceptance criteria:

- Fonebrew can reference only existing verified findings.
- Fonebrew cannot record proof or human decisions.
- Studio and manual paths produce equivalent candidate contracts.

### Epic O: ASOM advisory explanations

Capabilities:

- user-selected model/provider;
- explanation grounded in normalized evidence and optionally approved source context;
- visible model, prompt policy and generation time;
- no automatic lifecycle transition;
- private/local model path where configured.

Acceptance criteria:

- Explanations are marked advisory and can be hidden without losing evidence.
- Model failure does not affect evidence readiness.
- No explanation text is copied into immutable scanner evidence.

## v2 Android experience

Recommended scope:

- connected read-only project list;
- project health and freshness;
- run, finding and candidate detail screens;
- search and filters;
- review queue with authenticated handoff;
- activity timeline;
- secure local cache and manual import fallback;
- accessibility and tablet/large-screen layouts;
- release-signed distribution.

Do not add direct mobile approval in the first v2 release. Treat it as a separate security epic requiring device trust, strong authentication, replay resistance, revocation, offline semantics and updated threat analysis.

## Suggested release slices

### v2.0: Operational visibility

- Project/run registry
- Connected read model
- Production identity and role mapping
- Findings workspace
- Android connected read-only mode
- Orrery multi-project status

### v2.1: Team review

- Triage annotations
- Accepted-risk workflow
- Decision queue
- Notifications and ownership
- Repository-host status checks

### v2.2: Policy and proof scale

- Versioned policy packs
- Baselines and differential audits
- Evidence signing
- Disposable proof workers
- Competing proposal comparison

### v2.3: Constellation refinement

- Fonebrew Studio handoff
- ASOM advisory provider controls
- Portfolio trends and escalation
- Cross-project policy views

## v3 horizon

Consider only after v2 usage validates demand:

- scanner/plugin SDK with signed plugins and capability declarations;
- organization-level policy simulation;
- richer secure supply-chain attestations;
- cross-repository dependency blast-radius analysis;
- remediation pattern library built from approved history;
- privacy-preserving aggregate analytics;
- multiple repository hosts and CI systems;
- controlled remote execution fleets;
- evidence federation between trusted Assay installations.

None of these should weaken deterministic finding provenance or human decision authority.

## Explicit non-goals

- Autonomous approval or merge.
- Treating AI confidence as scanner evidence.
- Replacing repository review and branch protection.
- A general-purpose project-management suite.
- Editing historical findings to match current triage opinion.
- Showing a project as healthy because data is missing.
- Adding enterprise complexity before multi-user needs are validated.

## Success measures

### Trust and correctness

- Percentage of runs with complete required scanner evidence.
- Number of stale or invalid actions blocked before decision/application.
- Signature and manifest verification success rate.
- Candidate records with complete source/finding/patch/test/proof binding.
- Zero machine-originated human decisions.

### Workflow quality

- Median time from valid finding to first candidate.
- Median proof queue and execution time.
- Median time awaiting reviewer decision.
- Reviewer rework caused by missing context.
- Percentage of source-drift invalidations explained and successfully reproposed.

### Operational quality

- Runner preflight pass rate and top failure causes.
- Cancellation cleanup success.
- MobSF cleanup success.
- Evidence publication conflict rate.
- Projects with valid evidence inside freshness policy.

### Product usefulness

- Maintainers who can correctly distinguish ready-clean from unavailable in usability testing.
- Reviewers who can reconstruct a decision from stored evidence.
- Portfolio observers who can correctly route blocked versus degraded projects.
- Percentage of Fonebrew proposals that enter proof, rather than raw proposal volume.

## Recommended next step

Do not start every v2 epic in parallel. Complete deployment certification, then run a short discovery and operational-observation cycle. The first implementation slice should be the project/run registry plus connected read-only projections because it improves repeated use without expanding mutation authority.