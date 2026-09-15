# Runner decision 1.1.0

The v1 engine is a one-shot Kotlin/JVM process. SSH is transport, not the trust boundary. A production runner checks out an immutable source commit in an isolated worktree, runs pinned deterministic tools in an ephemeral unprivileged outer sandbox, normalizes and validates artifacts, and only then publishes to `assay/audit` with push-with-lease.

The engine now provides command contracts and normalizers for Gitleaks, Semgrep, OSV-Scanner, and MobSF JSON reports. Local CLI scanner execution verifies both the binary SHA-256 and the scanner-reported version. This verification does not replace sandboxing.

Claude Code may later be invoked as a bounded AI adapter with noninteractive structured output and explicit cost/turn limits. It is not the runner, cannot create findings, and cannot publish scanner evidence.

The Dell homelab remains operationally unverified until the complete deliberately-vulnerable fixture succeeds there without artifact hand-editing, with network denied during scan execution and a scoped push credential introduced only after validation. No document or UI may label that path verified before this check.
