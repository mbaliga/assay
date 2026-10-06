# Runner operations

This runbook applies to the Dell homelab runner and any equivalent self-hosted Assay worker. The repository provides preflight and hardening artifacts; the physical host must still be exercised before it is marked certified.

## Host account and directories

Create a dedicated unprivileged account named `assay`. It must not have passwordless sudo. Install `ops/tmpfiles.d/assay.conf` as `/etc/tmpfiles.d/assay.conf`, then run:

```bash
sudo systemd-tmpfiles --create /etc/tmpfiles.d/assay.conf
```

The following paths are private to the `assay` account:

- `/var/lib/assay/home`
- `/var/lib/assay/work`
- `/var/lib/assay/status`
- `/var/lib/assay/candidates`
- `/var/lib/assay/mobsf`

The scanner distribution, tool lock and runner scripts should be root-owned and not writable by `assay` under `/opt/assay`.

## Tool installation

Install the exact Gitleaks, Semgrep and OSV-Scanner binaries referenced by the tool lock. Do not substitute a package-manager binary without updating and reviewing the lock. Install Git and a rootless-capable container runtime such as Podman.

Run the preflight before registering or starting jobs:

```bash
/opt/assay/bin/assay runner-preflight \
  --work-root /var/lib/assay/work \
  --tool-lock /opt/assay/tool-lock.json \
  --gitleaks /opt/assay/tools/gitleaks \
  --semgrep /opt/assay/tools/semgrep \
  --osv /opt/assay/tools/osv-scanner \
  --git /usr/bin/git \
  --container-runtime /usr/bin/podman \
  --output /var/lib/assay/status/runner-preflight.json
```

The command fails unless the host is Linux, execution is non-root, cgroup v2 is present, the workspace is private, capacity is sufficient, scanner digests and reported versions match, and Git/container runtime version checks succeed.

## systemd

Install:

- `scripts/runner-preflight.sh` as `/opt/assay/bin/runner-preflight.sh` with mode `0755` and root ownership;
- `ops/systemd/assay-runner-preflight.service` as `/etc/systemd/system/assay-runner-preflight.service`;
- `ops/systemd/assay-actions-runner.service.d/hardening.conf` under the actual GitHub runner unit name, adjusting the drop-in directory only if the service has a different name.

Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable assay-runner-preflight.service
sudo systemctl start assay-runner-preflight.service
sudo systemctl restart assay-actions-runner.service
```

The runner unit requires successful preflight and applies capability removal, no-new-privileges, protected system/home paths, private temporary storage and task/file limits.

## Job isolation

Every job must:

1. create a new private workspace;
2. check out an exact full source commit;
3. verify the tool lock before scanner execution;
4. avoid inheriting interactive shell configuration;
5. restrict network access to operations that explicitly require it;
6. use a dedicated MobSF container and state directory;
7. publish only redacted validated artifacts;
8. remove worktrees, containers, credentials and temporary files on success, failure or cancellation.

Do not reuse a dirty source worktree between repositories or candidates.

## Audit publication

Publish verified local bus content to the disconnected `assay/audit` branch using an explicit expected remote commit:

```bash
assay publish-bus-git \
  --repo /var/lib/assay/work/source \
  --bus /var/lib/assay/work/audit \
  --source-commit <source-sha> \
  --expected-remote absent \
  --remote origin
```

Subsequent runs must replace `absent` with the last observed audit-branch commit. The command uses compare-and-swap push-with-lease and verifies the resulting remote ref. A stale lease is a failed publication, not a successful retry.

## MobSF

Generate the pinned container command with:

```bash
assay print-mobsf-container-command \
  --runtime /usr/bin/podman \
  --state /var/lib/assay/mobsf \
  --version <version> \
  --image <repository>@sha256:<digest>
```

The generated command binds MobSF to loopback, disables image pulls, drops capabilities, enables no-new-privileges, uses a read-only root filesystem and applies CPU, memory and process limits.

Store the MobSF API key in a private regular file and run analysis through `run-mobsf`. The client always attempts `delete_scan`, including after report failure.

## Certification checklist

A host is certified only after recording evidence that:

- preflight passes on the actual host;
- a fixture repository is scanned by all pinned local scanners;
- a real APK is scanned by the pinned MobSF image;
- cancellation removes the worktree and container;
- a first and subsequent `assay/audit` publication succeeds;
- a deliberately stale lease is rejected;
- the Android console can import the generated snapshot;
- no token, secret or private repository content appears in audit artifacts.
