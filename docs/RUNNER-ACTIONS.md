# Hosted runner: GitHub Actions (runner v1)

Status: `verified` on a GitHub-hosted runner by the sample end-to-end run recorded in [STATUS.md](STATUS.md). The self-hosted path is not verified.

[RUNNER-DECISION.md](RUNNER-DECISION.md) requires a one-shot Kotlin/JVM process on an isolated
Linux host. A GitHub-hosted `ubuntu-latest` job is that process: a fresh VM per job, destroyed at
the end. The self-hosted Dell path ([RUNNER-OPERATIONS.md](RUNNER-OPERATIONS.md)) stays valid and
uses the same CLI and artifacts; nothing here assumes it.

## How a caller uses it

```bash
assay init-actions --repo .          # writes .github/workflows/assay.yml
git add .github/workflows/assay.yml && git commit -m "Add Assay audit" && git push
```

The generated workflow is a `workflow_dispatch` caller of
`mbaliga/assay/.github/workflows/assay-audit.yml@<ref>`. (The reusable workflow lives under
`.github/workflows/` because GitHub only resolves reusable workflows from there.) Pin `<ref>` to a
commit SHA; `main` is accepted but unpinned.

Inputs: `output_branch`, `scanners` (subset of `gitleaks,semgrep,osv,mobsf`), `build_apk`,
`target_dir`, `source_ref`, `assay_ref`, `semgrep_config`, `ai` (only `off` until WP4).

## Conformance to the runner decision

| Requirement | How the job meets it |
|---|---|
| One-shot process | One job; `assay audit` runs once and exits 0, 2 or 3. |
| Isolated, ephemeral Linux host | Fresh GitHub-hosted VM per job; nothing persists. |
| Immutable source commit | `actions/checkout` at `source_ref`; `assay audit` refuses to run unless `--commit` equals the worktree `HEAD`. |
| Pinned tools | Gitleaks and OSV-Scanner are downloaded by SHA-256; Semgrep by exact version; the tool lock records the SHA-256 of each executable and `ScannerOrchestrator` re-checks digest and reported version before every run. MobSF is pulled by image digest. |
| No inherited shell configuration | Scanner subprocesses start with a cleared environment (`JvmCommandRunner`). |
| Network per step | See below. |
| Credentials only after validation | See below. |
| Cleanup on success, failure, cancel | An `if: always()` step removes the MobSF container, firewall rule, network and secrets; the VM is destroyed regardless. |
| Redacted, validated artifacts only | Output is redacted, scanned twice for secrets, and JSON-Schema validated before it is staged. |

### Network discipline per step

| Step | Network |
|---|---|
| Checkout, tool downloads, `pip install semgrep`, `docker pull` | Open (these are the only places pinned artifacts enter). |
| Target `./gradlew assembleDebug` (optional) | Open for dependency resolution. Runs **before** any push credential exists. |
| MobSF analysis | Container on a dedicated Docker network. A `DOCKER-USER` rule rejects everything leaving that subnet; the job then tries to open a connection from inside the container and fails the job if it succeeds. The API is reached on `127.0.0.1` only. |
| Gitleaks, Semgrep | Local only (Semgrep uses a pinned local rules file, metrics and version checks off). |
| OSV-Scanner | `--online` is explicit in the workflow because a hosted runner has no preloaded advisory database. This is the one scanner step with outbound access; it sends dependency coordinates to osv.dev. |
| `git push` of `.assay/` | Open to the caller's remote only. |

### Credential handling

The target checkout uses `persist-credentials: false`. The repository token reaches the job only
as an environment variable of the `assay audit` step, is turned into a git `http.extraheader` for
that process's `git` children, and is unset from the shell first. Scanners and MobSF never see it
because the JVM starts them with a cleared environment. The token has `contents: write` only.

## What the hosted runner cannot guarantee

- **Host kernel and hypervisor.** We do not control them. A malicious target could attack the VM;
  the blast radius is one ephemeral VM with a repo-scoped token that exists only in the audit step.
- **The optional APK build runs the target's Gradle build.** That is arbitrary code from the
  scanned repository, on the same VM as the later steps. It runs without credentials and before
  MobSF starts, but it is not sandboxed from the VM. Leave `build_apk` off for repositories you do
  not trust, or build the APK in your own workflow and scan it.
- **MobSF image trust rests on the digest pin.** The pinned digest was read from Docker Hub for tag
  `v4.5.1`; its contents were not independently audited. MobSF is treated as untrusted code.
- **JADX is provisioned by the runner.** MobSF 4.5.1 downloads JADX 1.5.0 at first start; with its network denied the job instead downloads the release zip, checks it against a SHA-256 recorded when it was first fetched (trust on first use, not an independently published digest), and places it where MobSF looks.
- **Semgrep binary integrity.** It is installed by pinned version from PyPI, not by SHA-256 of a
  release artifact, so the lock records the digest of what was installed rather than verifying it
  against an independent value.
- **OSV egress.** See above.
- **Branch protection.** The job can push to the output branch; GitHub branch rules on that branch
  can reject it, which fails the audit.
