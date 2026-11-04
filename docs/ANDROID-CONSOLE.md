# Android app

The Android app under `android-console/` is the user-facing Assay review surface. It uses package name `dev.assay` and has three offline first-run paths plus a GitHub control plane. The `INTERNET` permission is used only by actions the user starts with a tap (see "Control plane").

## Start paths

### Scan an APK

Select an APK through Android's system document picker. Assay copies it into a private temporary file, performs a bounded deterministic inspection, creates an in-memory report and deletes the temporary copy.

The local quick check currently reviews:

- package identity, version, SDK range, file size and SHA-256;
- signing-certificate presence;
- debuggable and test-only flags;
- backup and cleartext-traffic flags;
- dangerous and broad/special permissions;
- exported activities, services, receivers and providers without guarding permissions;
- native shared-library presence.

This is deliberately labelled **Local quick check**. It is useful immediately and does not upload the APK, but it is not equivalent to the full runner audit. It does not run Gitleaks, Semgrep, OSV-Scanner or MobSF, does not inspect source history, and does not create tamper-evident audit evidence or remediation candidates.

### Open verified audit

Import the JSON produced by `assay console-snapshot`. The app validates the complete projection before displaying repository identity, source commit, run state, deterministic findings and remediation candidates.

### Explore a sample project

Open built-in, clearly labelled sample data to understand finding details, candidate lifecycle and the approval/rejection handoff before configuring a trusted runner. Sample data is never presented as real evidence.

## Control plane (WP3)

Five destinations: **Home, Repos, Runs, Candidates, Settings**. The app is a control plane, not a findings dashboard: it starts a run, shows its state, and hands the result to Fonebrew.

- **Settings** stores a fine-grained GitHub token (Contents: read/write, Actions: write on the audited repos) encrypted with a non-exportable Android Keystore key, kept out of backups. **Check token** calls `GET /user`.
- **Repos**: add `owner/repo` and an output branch. **Check** reports whether `.github/workflows/assay.yml` exists; **Add workflow** opens a pull request that adds it (a new branch, never the default branch, never merged by Assay).
- **Runs**: **Run audit now** sends `workflow_dispatch` to `assay.yml` with `output_branch`, `scanners` and `build_apk`. **Refresh** reads the latest Actions run and `.assay/assay-index.v1.json` from the output branch. State is shown as Queued, Running, Complete, Partial, Failed or No run yet, each with a shape and a label. A failed workflow whose index (for the same source commit) says `PARTIAL` is shown as Partial, because the audit exits non-zero when a scanner failed. The screen shows counts and the Fonebrew hand-off line, never findings.
- **Candidates**: **Load candidates** reads `console.json` from the `assay/audit` branch and shows candidate states (read-only in this build). Until the engine publishes `console.json` there, the screen says so rather than showing nothing.

Every network call is a blocking function marked `@NetworkAction` that a button starts. `NetworkPolicyTest` fails the build if one is reachable from a lifecycle callback, an `init` block, a Compose effect or any background scheduler (WorkManager, JobScheduler, AlarmManager, broadcast receivers, connectivity callbacks). Cleartext traffic and backup stay off. There is no analytics.

Without a token the app still works offline: quick check, snapshot import and sample data.

## Trust labels

The app keeps these states visually and semantically distinct:

- **Local quick check** — generated on the phone from an APK selected by the user;
- **Runner-verified evidence** — a projection that passed the strict Assay snapshot contract;
- **Sample data** — a guided demonstration with no evidentiary meaning;
- **Unavailable or invalid evidence** — never converted into zero findings.

The Android app cannot originate runner findings, record proof, approve, reject, apply or merge a remediation. For proof-passed candidates it may copy an explicit CLI command, but execution remains on the trusted runner and is revalidated there.

## Produce a verified snapshot

```bash
assay console-snapshot \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/console.json
```

Transfer that file through an authenticated channel and select **Open verified audit**.

## Build

The app is an independent Gradle project so Android dependencies cannot affect the JVM scanner engine.

```bash
gradle -p android-console :app:lintDebug :app:assembleDebug :app:testDebugUnitTest :app:verifyRoborazziDebug --no-daemon
```

The project needs `git submodule update --init` first: `dev.aarso:crash-recovery` is consumed from the pinned `android-console/shared-libraries` submodule (Shared-Libraries-asoc) through a composite build, as elsewhere in the constellation. Re-record screenshots after an intentional UI change with `:app:recordRoborazziDebug`. On a host where Maven Central rate-limits, pre-download Robolectric's `android-all-instrumented` jar and pass `-ProbolectricDependencyDir=<dir>`.

The debug APK is written under:

```text
android-console/app/build/outputs/apk/debug/
```

GitHub Actions workflow `Android console` runs lint, assembles the debug APK and uploads the APK and lint reports as the `assay-android-console-debug` artifact.

## Verified snapshot validation

The app rejects snapshots that violate any of these conditions:

- schema version is not `1.1.0`;
- unknown or missing JSON fields;
- file exceeds 8 MB;
- invalid SHA-1, SHA-256, repository or candidate identifiers;
- unsupported availability, severity or lifecycle values;
- duplicate findings or candidates;
- a finding references a missing candidate;
- a candidate references a missing finding;
- a non-ready snapshot contains findings or candidates;
- a ready snapshot contains an error reason.

The app validates the projection format. The trusted runner remains responsible for validating source artifacts, manifests, proof, candidate event chains and publication history before projection.

## Local APK handling

- The document picker grants access only to the APK selected by the user.
- The APK is copied to the app's private cache with a 1 GB maximum.
- Analysis runs off the main thread.
- The temporary APK is deleted in a `finally` block whether analysis passes or fails.
- Reports remain in memory and are not persisted automatically.
- No Internet permission is requested, so local APK content cannot be uploaded by the app.

## Approval handoff

For a proof-passed candidate, the app can copy commands shaped like:

```bash
assay candidate-approve \
  --store <candidate-store> \
  --candidate <candidate-id> \
  --revision <revision> \
  --actor human:<reviewer>
```

The placeholder values must be resolved on the trusted runner. Copying a command is not approval; the lifecycle engine verifies the actor, proof, revision and stored candidate when the command executes.
