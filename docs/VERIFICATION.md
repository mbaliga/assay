# Verification matrix

Verification is split into three levels:

- **implemented** — code and contracts exist in this repository;
- **mechanically verified** — repository-controlled acceptance or CI evidence exists;
- **environment-certified** — the capability has also been exercised in the actual deployment environment.

No row may be promoted by prose. Promotion requires the named mechanical or deployment evidence.

| Capability | Current evidence | Status |
|---|---|---|
| Pure-JVM core and packaged CLI | GitHub `gradle check` and `installDist` | mechanically verified |
| Canonical SARIF, severity, redaction, bus, proof and candidate model | deterministic acceptance suites | mechanically verified |
| Gitleaks, Semgrep and OSV command, version and digest enforcement | acceptance suites plus live scanner-contract workflow | mechanically verified |
| Gitleaks, Semgrep and OSV real CLI contract | pinned binaries exercised by `scanner-contracts.yml` | mechanically verified |
| MobSF API lifecycle | fake HTTP server verifies upload, scan, report, error and delete cleanup | mechanically verified against the API contract |
| Pinned MobSF container scanning a real APK | actual pinned container and APK required | implemented; environment certification pending |
| Local atomic audit directory | acceptance tests | mechanically verified |
| Remote `assay/audit` disconnected publication | real local bare Git remote verifies orphan history and stale lease rejection | mechanically verified locally |
| Hosted `assay/audit` publication | production Git credentials and remote required | implemented; environment certification pending |
| Candidate JSON persistence and locking | round-trip, tamper, stale revision, symlink and concurrent-process tests | mechanically verified |
| Git fix branch and application contract | temporary real Git repositories | mechanically verified |
| End-to-end candidate CLI lifecycle | real temporary Git worktree and packaged command tests | mechanically verified |
| Fonebrew proposal gateway | integration acceptance tests | mechanically verified at file/CLI boundary |
| Orrery status projection | integration acceptance tests | mechanically verified at file/CLI boundary |
| Optional ASOM explanation boundary | read-only integration acceptance tests | mechanically verified at type boundary |
| Dell/self-hosted runner preflight | fake-tool and environment acceptance tests | mechanically verified; Dell certification pending |
| Android 1.1 first-run home | APK assembly and lint workflow | mechanically verified |
| Android local APK quick check | compiler/lint verification of bounded private-copy inspection, API guards and cleanup path | mechanically verified in build; physical-device smoke test pending |
| Android verified snapshot parser and review UI | strict parser implementation plus APK assembly/lint | mechanically verified in build; physical-device smoke test pending |
| Android sample project | built-in clearly labelled sample data plus APK assembly/lint | mechanically verified |
| Android release signing and distribution | release credentials and physical device required | pending |

## Latest exact-head CI evidence

Head: `16dab44597dc1255424b056e0844784f972cd5b5`

- Core CI: passed — run `30829490191`
- Real scanner contracts: passed — run `30829484868`
- Android 1.1 APK assembly and lint: passed — run `30829485024`
- Android artifact: `8862382197`
- Android artifact ZIP SHA-256: `999fa60c1cf12660ef2eea4540c90049ce29213425009a3395e2c41b0add93b9`
- Extracted debug APK SHA-256: `187b66ca35696ebde979afe002d0eea1673ca7a54520cdb05506c1781fc44944`

## Remaining deployment certification

Before calling a deployment fully certified, record evidence for all applicable items:

1. Dell runner installed under a dedicated non-root account.
2. Runner preflight succeeds against installed tools and private work directories.
3. Cancellation and restart leave no source, APK, credential or temporary evidence behind.
4. Pinned MobSF container scans a representative APK and deletes the uploaded sample.
5. Production credentials publish and re-read the hosted `assay/audit` branch with a correct lease.
6. Assay 1.1 installs on a physical Android device.
7. **Scan an APK** completes against at least one valid APK and one malformed file.
8. **Open verified audit** accepts a real valid snapshot and rejects a tampered snapshot.
9. TalkBack, text scaling, dark/light appearance and long-content scrolling receive a device smoke test.
10. Release signing, retention and distribution procedures are recorded.
11. Installed Fonebrew and Orrery transports preserve source identity and authority boundaries.
12. Optional ASOM is exercised only where an actual provider is configured.
