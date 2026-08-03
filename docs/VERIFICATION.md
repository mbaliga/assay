# Verification matrix

No capability is promoted by prose. A row is marked verified only when the named mechanical evidence exists. Environment certification remains separate from repository verification.

| Capability | Mechanical evidence | Repository status | Deployment status |
|---|---|---:|---:|
| Core build and packaged CLI | GitHub `gradle check`, `installDist`, distribution artifact | verified | portable distribution produced |
| Canonical SARIF, severity mapping, redaction and bus validation | deterministic acceptance fixtures and schema checks | verified | requires deployment-specific paths and permissions |
| Gitleaks, Semgrep and OSV command/digest/version enforcement | JVM acceptance tests | verified | pinned executables still required on each runner |
| Real Gitleaks, Semgrep and OSV CLI contract | dedicated GitHub Actions workflow executing packaged pinned scanners | verified | rerun on the Dell host during certification |
| Candidate identity and lifecycle engine | lifecycle acceptance suite | verified | no automatic merge path exists |
| Candidate JSON persistence | codec, tamper, symlink, lock and stale-revision acceptance tests | verified | private durable candidate directory required |
| Git-bound candidate workflow | temporary-repository create/propose/prepare/commit/proof/approve/apply test | verified | repository credentials and review policy remain deployment concerns |
| Local atomic audit bus | staged publication, manifest, gate and failure-injection tests | verified | private local audit directory required |
| Remote `assay/audit` publication | real local bare-Git remote, disconnected first commit, parent-chain and stale-lease tests | verified | live hosted remote and credentials require field certification |
| MobSF API lifecycle | local HTTP contract server verifies upload, scan, report, normalization and delete-on-failure | verified | pinned MobSF image and real APK require field certification |
| MobSF container command | immutable image-digest and least-privilege command assertions | verified | actual Podman/Docker runtime and image require field certification |
| Fonebrew proposal boundary | gateway test rejects unknown findings and creates only finding-bound proposals | verified | installed Fonebrew transport requires field certification |
| Orrery status projection | health projection and fail-closed snapshot tests | verified | installed Orrery transport requires field certification |
| Optional ASOM boundary | read-only advisory response type and validation | verified | model provider transport is optional and requires field certification |
| Runner preflight | host, permission, capacity, binary-digest, version, Git and container-runtime tests | verified | Dell hardware must pass the command before certification |
| Runner service hardening | systemd preflight unit, runner drop-in and private tmpfiles configuration | reviewed and committed | installation and cancellation cleanup require Dell-host testing |
| Android console parser and UI | Android debug APK build and lint workflow | verified when the Android workflow is green on the PR head | device import/usability smoke test requires an Android device |
| Android console artifact | workflow-uploaded debug APK and lint reports | verified when artifact upload succeeds on the PR head | release signing is intentionally outside this draft PR |

## Required deployment certification

The repository-contained v1 is complete when all PR-head workflows are green. Production or homelab certification additionally requires evidence from the actual environments:

1. run `runner-preflight` successfully on the Dell host;
2. execute all pinned local scanners against a fixture repository on that host;
3. start the pinned MobSF image and scan a real APK;
4. prove cancellation removes worktrees, credentials and containers;
5. publish first and subsequent commits to the real `assay/audit` remote and reject a stale lease;
6. import the generated console snapshot into the built Android app;
7. exercise installed Fonebrew and Orrery transports, and ASOM only when configured;
8. inspect all published artifacts for secrets and private source leakage.

Until those steps are recorded, the repository may be described as implementation-complete and CI-verified, but not Dell-, MobSF-, device- or constellation-certified.

Nothing in this matrix authorizes automatic merging. The maximum automated status remains `ready-for-human-review`.
