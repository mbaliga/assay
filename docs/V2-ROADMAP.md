# Assay v2 roadmap

v1 establishes the deterministic trust engine, evidence bus, candidate lifecycle, narrow integrations and runner-hardening assets. Android 1.1 adds an immediately usable offline APK quick check, guided sample exploration and strict runner-evidence import.

v2 should turn those primitives into an operational product for repeated use across projects and teams without weakening the authority model or confusing local package inspection with source-bound audit evidence.

This roadmap separates deployment certification from new product capability. Uncertified v1 deployment work is not rebranded as v2.

## Before v2: deployment certification and v1.1 hardening

Required deployment certification:

1. Install the Dell/self-hosted runner and capture successful preflight evidence.
2. Exercise cancellation, cleanup, workspace isolation and restart recovery.
3. Run the pinned MobSF container against a representative real APK.
4. Publish and verify the real hosted `assay/audit` branch using production credentials.
5. Install Android 1.1 on a physical device.
6. Run the local quick check against a valid APK and malformed file.
7. Import a real valid snapshot and reject a tampered snapshot.
8. Complete TalkBack, text-scaling, dark/light and long-content smoke tests.
9. Establish stable release signing, artifact retention and distribution procedure.
10. Connect and exercise installed Fonebrew and Orrery transports.
11. Exercise optional ASOM only when a provider is configured.
12. Review the threat model against the actual network, identity and credential topology.

Recommended v1.1 hardening after live use:

- validate package-level rules against representative debug, release and third-party APKs;
- improve explanations and remediation guidance for local observations;
- define export/share semantics for local reports without overstating assurance;
- add structured operational error codes;
- strengthen cancellation-safe temporary-directory cleanup;
- define disk-pressure and evidence-retention policy;
- document backup/restore for candidate records and audit history;
- review log redaction using real failures;
- document reproducible CLI/APK release builds;
- migration-test every schema version change.

## v2 product thesis

Assay v2 should answer:

> Across the Android projects I am responsible for, what evidence is valid, what changed, which findings require action, which remediations are awaiting proof or decision, and what can I safely do next?

The v2 product is not an autonomous repair agent. It is a multi-project evidence, review and controlled-remediation system. The local APK quick check remains a useful offline utility, not a substitute for the project audit model.

## v2 principles

- Deterministic scanners remain the only runner-finding writers.
- Local APK observations remain separate from source findings.
- Evidence and candidate identities remain immutable.
- Human decisions remain explicit and attributable.
- Convenience is added through projections, queues and integrations rather than hidden authority.
- Policy is versioned and retained with historical runs.
- Accepted risk and false-positive triage are annotations, not edits to findings.
- Automatic merge is not a default or implied end state.
- Offline local inspection and manual import remain available when connected services are absent.

## Priority 0: operational product foundation

### Epic A: Project and run registry

Capabilities:

- register repositories and ownership;
- record latest verified source revision and run;
- preserve run history and policy/toolset versions;
- distinguish never run, unavailable, invalid, stale, ready with findings and ready clean;
- generate read-only project summaries for Android and Orrery;
- avoid storing repository credentials in project records.

Acceptance criteria:

- No project appears healthy without a valid run inside freshness policy.
- Every run links to immutable evidence and exact source commit.
- Deleting a project registration does not rewrite audit history.

### Epic B: Connected read model

Replace manual snapshot transfer as the only convenient runner-evidence path while preserving offline import.

Capabilities:

- authenticated read-only API or signed snapshot feed;
- incremental retrieval of projects, runs, findings and candidates;
- Android cache with explicit freshness and source identity;
- offline/manual import remains supported;
- no mobile mutation authority in this epic.

Acceptance criteria:

- Connected and imported views validate equivalent schemas and invariants.
- Stale cache cannot appear current without a visible warning.
- Local APK reports are never uploaded or inserted into project audit history implicitly.

### Epic C: Production identity and authorization

Capabilities:

- authenticated human and service identities;
- project-scoped maintainer, reviewer, operator and observer roles;
- reviewer eligibility policy;
- service credentials for runner, Fonebrew and Orrery;
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
- candidate coverage and remediation state;
- advisory ASOM explanation separated from evidence.

Acceptance criteria:

- Comparison explains incompatibility when policy or identity rules changed.
- Filtering never changes counts without indicating active filters.
- Raw evidence remains locatable.

### Epic E: Triage annotations and accepted risk

Capabilities:

- owner, status and reviewer notes;
- false-positive assertion;
- accepted-risk waiver with reason, approver, scope and expiry;
- duplicate and related-finding links;
- automatic waiver expiry and re-review queue;
- policy restrictions by severity or rule.

Acceptance criteria:

- A waiver never removes a historical finding.
- Expired waivers cannot suppress current health silently.
- Exceptions are attributable and time-bound by default.

### Epic F: Decision queue

Capabilities:

- proof-passed candidates awaiting review;
- complete candidate/proof context;
- approve/reject with rationale;
- reviewer eligibility and optional two-person policy;
- invalidation after revision or source changes;
- notification and escalation without auto-decision.

Acceptance criteria:

- A decision cannot execute against a stale revision.
- UI distinguishes proof passed, approved, applied, merged and released.
- Mobile approval remains out of scope until a dedicated threat-model review.

## Priority 2: policy and evidence intelligence

### Epic G: Versioned policy packs

Capabilities:

- project and organization policy layers;
- required scanner rules;
- severity mapping and gates;
- evidence freshness policy;
- waiver restrictions;
- historical dry-run;
- signed/versioned release.

Acceptance criteria:

- Historical conclusions retain execution policy version.
- Policy changes create new evaluations rather than rewriting evidence.
- Missing required scanners block readiness.

### Epic H: Baselines and differential audits

Capabilities:

- compatible baseline selection;
- added, resolved and persistent findings;
- release/protected branch baseline rules;
- differential gates for new findings;
- periodic full-run requirement.

Acceptance criteria:

- Differential mode cannot hide unavailable scanners.
- Baseline identity and compatibility are visible.
- Clean differential is not presented as clean full audit.

### Epic I: Evidence attestation

Capabilities:

- sign run manifests and projections;
- record runner/environment identity;
- verify signatures before connected clients trust data;
- key rotation and revocation;
- portable evidence bundle.

Acceptance criteria:

- Signature failure produces invalid evidence.
- Signing keys never appear in artifacts or mobile storage.
- Rotation preserves historical verification.

## Priority 3: safe remediation at team scale

### Epic J: Disposable proof workers

Capabilities:

- isolated proof job per candidate;
- bounded CPU, memory, network and time;
- explicit dependency/cache policy;
- queue, cancellation and retry;
- tamper-evident proof retention;
- flakiness diagnostics.

Acceptance criteria:

- Proof workers cannot approve, apply or merge.
- Retry cannot silently overwrite a proof record.
- Network is denied by default and declared when required.

### Epic K: Repository-host integration

Capabilities:

- create/update draft PRs after proof and approval;
- attach immutable evidence locators and candidate identity;
- status checks for evidence, proof, approval and freshness;
- observe merge and trigger a new audit;
- preserve manual operation.

Acceptance criteria:

- Assay never presses merge by default.
- Repository protections remain authoritative.
- Merge observation creates new evidence, not rewritten history.

### Epic L: Competing proposal comparison

Capabilities:

- multiple candidates for one finding;
- compare patch scope, test coverage, proof and residual evidence;
- supersede or withdraw without deleting history;
- preserve independent identities.

Acceptance criteria:

- Selecting one proposal does not erase alternatives.
- Reviewers can see why a candidate was superseded.

## Priority 4: Android and constellation experience

### Epic M: Android connected review

Capabilities:

- connected read-only project list;
- project health and freshness;
- run, finding and candidate detail screens;
- search and filters;
- review queue with authenticated handoff;
- activity timeline;
- secure cache and manual import fallback;
- accessibility and tablet layouts;
- release-signed distribution.

The home screen retains **Scan an APK** as an offline utility. Local reports have their own history/export model only if user research validates it, and never become runner findings automatically.

### Epic N: Local APK inspection maturity

Capabilities to validate before implementation:

- export/share with explicit local-assurance metadata;
- certificate lineage and signing-scheme detail;
- compare APK package metadata between builds;
- configurable local-rule explanations;
- safer classification of exported components and permissions;
- optional local report retention with user-controlled deletion.

Acceptance criteria:

- No local result is labelled verified audit evidence.
- Reports state the APK hash, check version and limitations.
- APK bytes remain local unless the user explicitly performs a separately described action.

### Epic O: Orrery portfolio posture

Capabilities:

- health and evidence freshness;
- unresolved-risk ageing;
- blocked-run root causes;
- awaiting-proof/review counts;
- ownership and escalation;
- read-only drill-down.

Acceptance criteria:

- Unknown and blocked never aggregate into healthy.
- Metrics link to verifiable source objects.
- Orrery receives no mutation capability.

### Epic P: Fonebrew Studio workflow

Capabilities:

- context-rich finding-to-proposal handoff;
- secure patch/test transfer and digests;
- proposal progress visible in Fonebrew;
- manual equivalent without Studio;
- clear authority boundary.

Acceptance criteria:

- Only existing verified findings may be referenced.
- Fonebrew cannot record proof or decisions.
- Studio/manual paths produce equivalent contracts.

### Epic Q: ASOM advisory explanations

Capabilities:

- user-selected provider;
- explanation grounded in normalized evidence;
- visible model, prompt policy and generation time;
- no automatic transition;
- private/local model path where configured.

Acceptance criteria:

- Explanations are advisory and removable without losing evidence.
- Model failure does not affect readiness.
- Explanation text is not copied into scanner evidence.

## Suggested release slices

### v2.0: Operational visibility

- Project/run registry
- Connected read model
- Production identity and roles
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

### v2.3: Android and constellation refinement

- Mature local APK report experience if validated
- Fonebrew Studio handoff
- ASOM advisory controls
- Portfolio trends and escalation
- Cross-project policy views

## v3 horizon

Consider only after v2 usage validates demand:

- signed scanner/plugin SDK;
- organization policy simulation;
- richer supply-chain attestations;
- cross-repository dependency blast-radius analysis;
- remediation pattern library from approved history;
- privacy-preserving aggregate analytics;
- multiple repository hosts and CI systems;
- controlled remote execution fleets;
- evidence federation between trusted Assay installations.

## Explicit non-goals

- Autonomous approval or merge.
- Treating AI confidence as evidence.
- Treating local APK inspection as source audit evidence.
- Replacing repository review and branch protection.
- General-purpose project management.
- Editing historical findings to match current triage opinion.
- Showing health because data is missing.
- Enterprise complexity before multi-user needs are validated.

## Success measures

### Trust and correctness

- Runs with complete required scanner evidence.
- Stale or invalid actions blocked before decision/application.
- Candidate records with complete source/finding/patch/test/proof binding.
- Zero machine-originated human decisions.
- Users who correctly distinguish local quick check, sample and runner evidence.

### Workflow quality

- Time from valid finding to first candidate.
- Proof queue and execution time.
- Time awaiting reviewer decision.
- Reviewer rework caused by missing context.
- Source-drift invalidations explained and successfully reproposed.

### Operational quality

- Runner preflight pass rate and failure causes.
- Cancellation cleanup success.
- MobSF cleanup success.
- Evidence publication conflict rate.
- Projects with valid evidence inside freshness policy.

### Android usefulness

- First-launch users who successfully complete a meaningful path.
- Local APK inspection completion and parse-failure rates.
- False-positive/clarity results from moderated testing.
- Users who know when to escalate from local quick check to runner audit.
- Physical-device accessibility conformance.

## Recommended next step

Do not start every v2 epic in parallel. First complete deployment certification and observe real Android 1.1 and runner use. The first v2 implementation slice should be the project/run registry plus connected read-only projections because it improves repeated use without expanding mutation authority.