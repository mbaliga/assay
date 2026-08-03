# Android console

The Android console is a standalone read-only review app under `android-console/`. It uses the package name `dev.assay` and imports the JSON produced by `assay console-snapshot` through Android's system document picker.

## Capabilities

- distinguishes not configured, unavailable, invalid and ready evidence;
- shows repository, source commit, audit run and generated time;
- displays deterministic findings by severity, scanner, rule and location;
- displays candidate lifecycle, revision, fix branch and human-approval visibility;
- shows finding and candidate detail dialogs;
- copies explicit approval or rejection CLI commands for proof-passed candidates;
- supports system light and dark appearance;
- limits imported files, field sizes and displayed record counts.

The app requests no Internet permission. Imported evidence remains in memory and is not copied into app storage. It cannot create findings, run scanners, edit candidates, approve, reject, apply or merge changes directly.

## Produce a snapshot

```bash
assay console-snapshot \
  --bus /var/lib/assay/audit \
  --candidates /var/lib/assay/candidates \
  --expected-source-commit <source-sha> \
  --output /var/lib/assay/status/console.json
```

Transfer that file to the Android device using an authenticated channel, then select **Open verified snapshot**.

## Build

The console is an independent Gradle project so Android dependencies cannot affect the JVM scanner engine.

```bash
gradle -p android-console :app:lintDebug :app:assembleDebug --no-daemon
```

The debug APK is written under:

```text
android-console/app/build/outputs/apk/debug/
```

GitHub Actions workflow `Android console` runs lint, assembles the debug APK and uploads it as the `assay-android-console-debug` artifact.

## Snapshot validation

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

The Android app validates the projection format, while the runner remains responsible for validating source artifacts, manifests, proof and candidate event chains before projection.

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
