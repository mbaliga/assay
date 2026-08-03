# Verification matrix

Verification is split into three levels:

- **implemented** — code and contracts exist in this repository;
- **mechanically verified** — repository-controlled acceptance or CI evidence exists;
- **environment-certified** — the capability has also been exercised in the actual deployment environment.

No row may be promoted by prose. Promotion requires the named mechanical or deployment evidence.

## Capability matrix

| Capability | Current evidence | Status |
|---|---|---|
| Pure-JVM core and packaged CLI | GitHub `gradle check` and `installDist` | mechanically verified |
| Canonical SARIF, severity, redaction, bus, proof and candidate model | deterministic acceptance suites | mechanically verified |
| Gitleaks, Semgrep and OSV command, version and digest enforcement | acceptance suites plus live scanner-contract workflow | mechanically verified |
| Gitleaks, Semgrep and OSV real CLI contract | pinned binaries exercised by `scanner-contracts.yml` | mechanically verified |
| MobSF API lifecycle | fake HTTP server verifies upload, scan, report, error and delete cleanup | mechanically verified against API contract |
| Pinned MobSF container scanning a real APK | actual pinned container and APK required | implemented; environment certification pending |
| Local atomic audit directory | acceptance tests | mechanically verified |
| Remote `assay/audit` disconnected publication | real local bare Git remote verifies orphan history and stale lease rejection | mechanically verified locally |
| Hosted `assay/audit` publication | production Git credentials and remote required | implemented; environment certification pending |
| Candidate JSON persistence and locking | round-trip, tamper, stale revision, symlink and concurrent-process tests | mechanically verified |
| Git fix branch and application contract | temporary real Git repositories | mechanically verified |
| End-to-end candidate CLI lifecycle | real temporary Git worktree and packaged-command tests | mechanically verified |
| Fonebrew proposal gateway | integration acceptance tests | mechanically verified at file/CLI boundary |
| Orrery status projection | integration acceptance tests | mechanically verified at file/CLI boundary |
| Optional ASOM explanation boundary | read-only integration acceptance tests | mechanically verified at type boundary |
| Dell/self-hosted runner preflight | fake-tool and environment acceptance tests | mechanically verified; Dell certification pending |
| Android 1.1 first-run home | APK assembly and lint workflow | mechanically verified |
| Android local APK quick check | compiler/lint verification of bounded private-copy inspection, API guards and cleanup path | mechanically verified in build; physical-device smoke test pending |
| Android verified snapshot parser and review UI | strict parser implementation plus APK assembly/lint | mechanically verified in build; physical-device smoke test pending |
| Android sample project | built-in clearly labelled sample data plus APK assembly/lint | mechanically verified |
| Android no-network posture | manifest and lint verification; no Internet permission | mechanically verified in build |
| Android release signing and distribution | stable release credentials and physical device required | pending |
| Product definition: promise, IA, personas, journeys and v2 roadmap | repository documentation review | implemented; user-research validation pending |

## CI evidence policy

Exact current-head workflow run IDs belong in the pull-request description and GitHub Actions history, not in this committed file.

Reason: editing this file creates a new commit and therefore immediately makes any embedded “current head” SHA obsolete. The PR description can be updated without changing the branch head and is the authoritative exact-head verification record for the active PR.

For an exact release or deployment record, capture:

- commit SHA;
- workflow name and run ID;
- workflow conclusion;
- artifact ID and artifact digest;
- extracted APK/CLI digest when distributed;
- signing identity for a release build;
- date and responsible human/operator.

Repository-controlled verification lanes:

1. **CI** — JVM acceptance suites and packaged CLI.
2. **Scanner contracts** — real pinned Gitleaks, Semgrep and OSV execution contracts.
3. **Android console** — Android APK assembly, lint and artifact publication.

A release tag or deployment certification record should reference one exact commit for which all required lanes passed.

## Android physical-device certification

Before calling Android 1.1 device-certified, record evidence for:

1. Install the intended APK on at least one supported physical device.
2. Confirm the home screen exposes **Scan an APK**, **Open verified audit** and **Explore a sample project**.
3. Run **Scan an APK** against a valid debug APK.
4. Run it against a valid release APK.
5. Confirm malformed, empty and non-APK files fail without a clean-looking result.
6. Confirm an oversized-file path is rejected or safely bounded.
7. Confirm the temporary APK is not retained after success or failure.
8. Confirm the report is labelled **Local quick check**.
9. Confirm the app makes no network request and has no Internet permission.
10. Open a real valid runner snapshot.
11. Reject a tampered, oversized and schema-incompatible snapshot.
12. Confirm the sample project is always labelled **Sample data**.
13. Test TalkBack, text scaling, dark/light appearance, long content and scrolling.
14. Confirm copyable decision commands retain placeholders and do not execute in-app.
15. Record device model, Android version, APK digest, result and tester identity.

## Remaining deployment certification

Before calling a deployment fully certified, record evidence for all applicable items:

1. Dell runner installed under a dedicated non-root account.
2. Runner preflight succeeds against installed tools and private work directories.
3. Cancellation and restart leave no source, APK, credential or temporary evidence behind.
4. Pinned MobSF container scans a representative APK and deletes the uploaded sample.
5. Production credentials publish and re-read the hosted `assay/audit` branch with a correct lease.
6. Android physical-device certification above is complete.
7. Release signing, retention and distribution procedures are recorded.
8. Installed Fonebrew and Orrery transports preserve source identity and authority boundaries.
9. Optional ASOM is exercised only where an actual provider is configured.
10. Product personas and comprehension assumptions are validated with real users before being treated as research findings.

## Certification record template

```text
Capability:
Environment:
Repository/commit:
Workflow or command:
Artifact/image/tool digest:
Expected result:
Observed result:
Evidence location:
Operator/tester:
Date/timezone:
Limitations or follow-up:
```

A failed or partial result remains failed or partial. It must not be summarized as certified.